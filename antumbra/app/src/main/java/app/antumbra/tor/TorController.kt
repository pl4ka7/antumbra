package app.antumbra.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.antumbra.core.ConnectionMode
import app.antumbra.core.NetworkConfig
import app.antumbra.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService

sealed interface TorState {
    data object Idle : TorState
    data class Starting(val progress: Int, val summary: String, val since: Long) : TorState
    data object Ready : TorState
    data class Failed(val reason: String) : TorState
}

data class Relay(val nickname: String, val address: String?, val country: String?, val bridge: Boolean)
data class Circuit(val id: String, val relays: List<Relay>)

/**
 * Runs tor in-process through tor-android and observes it over the control socket.
 * tor can only be started once per process, so network changes require an app restart.
 */
class TorController(private val context: Context) {

    private val _state = MutableStateFlow<TorState>(TorState.Idle)
    val state: StateFlow<TorState> = _state.asStateFlow()

    /** Transport actually used for this run. */
    var mode: ConnectionMode = ConnectionMode.DIRECT
        private set
    var exitCountry: String = ""
        private set
    @Volatile var geoAvailable = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val transports by lazy { Transports(context) }
    private val bridges by lazy { Bridges(context) }
    @Volatile private var service: TorService? = null
    private var started = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as TorService.LocalBinder).service
            observe()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _state.value = TorState.Failed("tor stopped")
        }
    }

    fun start(network: NetworkConfig, effectiveMode: ConnectionMode, geo: () -> Geo.Files?) {
        if (started) return
        started = true
        mode = effectiveMode
        exitCountry = network.exitCountry
        _state.value = TorState.Starting(0, "", System.currentTimeMillis())
        scope.launch {
            try {
                val geoFiles = geo()
                geoAvailable = geoFiles != null
                val lines = bridgeLines(effectiveMode, network)
                val plugins = if (lines.isEmpty()) emptyMap() else transports.start(lines, network.upstreamProxy)
                TorService.getTorrc(context).writeText(torrc(network, geoFiles, lines, plugins))
                val bound = withContext(Dispatchers.Main) {
                    context.bindService(Intent(context, TorService::class.java), connection, Context.BIND_AUTO_CREATE)
                }
                if (!bound) _state.value = TorState.Failed("service unavailable")
            } catch (e: Exception) {
                _state.value = TorState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun bridgeLines(mode: ConnectionMode, network: NetworkConfig): List<String> = when (mode) {
        ConnectionMode.SNOWFLAKE -> bridges.forTransport("snowflake")
        ConnectionMode.OBFS4 -> bridges.forTransport("obfs4")
        ConnectionMode.MEEK -> bridges.forTransport("meek")
        ConnectionMode.CUSTOM -> network.customBridges
        ConnectionMode.DIRECT, ConnectionMode.AUTO -> emptyList()
    }

    private fun torrc(
        network: NetworkConfig,
        geo: Geo.Files?,
        bridgeLines: List<String>,
        plugins: Map<String, String>,
    ) = buildString {
        appendLine("SocksPort 127.0.0.1:$SOCKS_PORT IsolateSOCKSAuth")
        appendLine("HTTPTunnelPort 0")
        appendLine("DNSPort 0")
        appendLine("TransPort 0")
        appendLine("ClientOnly 1")
        appendLine("AvoidDiskWrites 1")
        appendLine("SafeLogging 1")
        if (geo != null) {
            appendLine("GeoIPFile ${geo.v4.absolutePath}")
            appendLine("GeoIPv6File ${geo.v6.absolutePath}")
            if (network.exitCountry.isNotBlank()) {
                appendLine("ExitNodes {${network.exitCountry}}")
                appendLine("StrictNodes 1")
            }
        }
        if (plugins.isNotEmpty()) {
            appendLine("UseBridges 1")
            plugins.forEach { (name, address) -> appendLine("ClientTransportPlugin $name socks5 $address") }
            bridgeLines.forEach { appendLine("Bridge $it") }
        } else {
            Settings.parseUpstream(network.upstreamProxy)?.let { (directive, hostPort) -> appendLine("$directive $hostPort") }
        }
    }

    private fun observe() = scope.launch {
        val progressPattern = Regex("PROGRESS=(\\d+)")
        val summaryPattern = Regex("SUMMARY=\"([^\"]*)\"")
        var answered = false
        var lastProgress = -1
        var since = System.currentTimeMillis()
        val startedAt = since
        while (isActive) {
            try {
                val next = control { ctl ->
                    val phase = ctl.getInfo("status/bootstrap-phase").orEmpty()
                    val progress = progressPattern.find(phase)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (progress != lastProgress) {
                        lastProgress = progress
                        since = System.currentTimeMillis()
                    }
                    val established = progress >= 100 && ctl.getInfo("status/circuit-established") == "1"
                    if (established) TorState.Ready
                    else TorState.Starting(progress, summaryPattern.find(phase)?.groupValues?.get(1).orEmpty(), since)
                }
                if (next != null) {
                    answered = true
                    _state.value = next
                }
            } catch (e: Exception) {
                // The control socket is published before it is authenticated; retry quietly at first.
                if (answered || System.currentTimeMillis() - startedAt > STARTUP_TIMEOUT_MS) {
                    _state.value = TorState.Failed(e.message ?: "control socket unavailable")
                }
            }
            delay(if (_state.value is TorState.Ready) 2_500 else 400)
        }
    }

    private suspend fun <T> control(block: (TorControlConnection) -> T): T? = withContext(Dispatchers.IO) {
        lock.withLock {
            val ctl = service?.torControlConnection ?: return@withLock null
            block(ctl)
        }
    }

    suspend fun newIdentity(): Boolean =
        runCatching { control { it.signal("NEWNYM") } != null }.getOrDefault(false)

    /** Circuit currently carrying traffic to [host], read from open streams. */
    suspend fun circuitFor(host: String): Circuit? = runCatching {
        control { ctl ->
            // stream-status: "<id> <status> <circuit> <target host:port>"
            val circuitId = ctl.getInfo("stream-status").orEmpty().lineSequence()
                .map { it.trim().split(' ') }
                .firstOrNull { it.size >= 4 && it[2] != "0" && it[3].substringBeforeLast(':').equals(host, true) }
                ?.get(2) ?: return@control null
            // circuit-status: "<id> BUILT $FP~nick,$FP~nick,... <flags>"
            val path = ctl.getInfo("circuit-status").orEmpty().lineSequence()
                .firstOrNull { it.startsWith("$circuitId ") }
                ?.split(' ')?.getOrNull(2) ?: return@control null
            val usesBridge = mode != ConnectionMode.DIRECT
            val relays = path.split(',').mapIndexed { index, hop ->
                val fingerprint = hop.substringBefore('~').removePrefix("$")
                // router status "r" line: r <nick> <id> <digest> <date> <time> <ip> <orport> <dirport>
                val address = runCatching { ctl.getInfo("ns/id/$fingerprint") }.getOrNull()
                    ?.lineSequence()?.firstOrNull { it.startsWith("r ") }?.split(' ')?.getOrNull(6)
                val country = address?.takeIf { geoAvailable }
                    ?.let { runCatching { ctl.getInfo("ip-to-country/$it") }.getOrNull() }
                    ?.takeIf { it.length == 2 && it != "??" }
                Relay(hop.substringAfter('~', fingerprint.take(8)), address, country, usesBridge && index == 0)
            }
            Circuit(circuitId, relays)
        }
    }.getOrNull()

    /** Refreshes the built-in bridge list; [fetch] must go through Tor. */
    suspend fun refreshBridges(fetch: suspend (String) -> String?) {
        if (!bridges.isStale) return
        fetch(Bridges.MOAT_BUILTIN)?.let { bridges.store(it) }
    }

    companion object {
        const val SOCKS_PORT = 9152
        private const val STARTUP_TIMEOUT_MS = 120_000L
    }
}
