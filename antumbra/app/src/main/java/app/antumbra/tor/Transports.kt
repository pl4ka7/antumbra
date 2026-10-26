package app.antumbra.tor

import IPtProxy.Controller
import IPtProxy.OnTransportEvents
import android.content.Context
import java.io.File

/** Pluggable transports (Lyrebird and Snowflake) provided by IPtProxy. */
class Transports(private val context: Context) {

    private val controller: Controller by lazy {
        val dir = File(context.noBackupFilesDir, "pt").apply { mkdirs() }
        Controller(dir.path, false, false, "ERROR", object : OnTransportEvents {
            override fun connected(name: String?) = Unit
            override fun error(name: String?, error: Exception?) = Unit
            override fun stopped(name: String?, error: Exception?) = Unit
        })
    }

    /**
     * Starts each transport used by [bridgeLines] and returns transport → local SOCKS address.
     * Snowflake does not support an upstream proxy, so the proxy is only passed to Lyrebird.
     */
    fun start(bridgeLines: List<String>, upstreamProxy: String): Map<String, String> {
        val names = bridgeLines.map { it.substringBefore(' ') }.toSet()
        bridgeLines.firstOrNull { it.startsWith("snowflake ") }?.let { line ->
            val args = Bridges.args(line)
            controller.snowflakeBrokerUrl = args["url"].orEmpty()
            controller.snowflakeFrontDomains = args["fronts"] ?: args["front"].orEmpty()
            controller.snowflakeIceServers = args["ice"].orEmpty()
        }
        return names.associateWith { name ->
            controller.start(name, if (name == "snowflake") "" else upstreamProxy)
            controller.localAddress(name).also {
                check(it.isNotBlank()) { "$name did not open a local port" }
            }
        }
    }
}
