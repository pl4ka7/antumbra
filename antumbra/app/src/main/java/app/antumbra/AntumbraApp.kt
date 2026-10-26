package app.antumbra

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import app.antumbra.core.ConnectionMode
import app.antumbra.core.SecretStore
import app.antumbra.core.Settings
import app.antumbra.system.KeepAliveService
import app.antumbra.tor.Geo
import app.antumbra.tor.TorController
import app.antumbra.web.Gecko
import app.antumbra.web.Isolation
import app.antumbra.web.Net
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

/**
 * Everything heavy is lazy: GeckoView content processes also create this Application,
 * and they must never start tor or a second runtime.
 */
class AntumbraApp : Application() {

    val settings by lazy { Settings(this) }
    val secrets by lazy { SecretStore(this) }
    val activeLevel by lazy { settings.securityLevel }
    val tor by lazy { TorController(this) }

    private var runtimeCreated = false
    val runtime: GeckoRuntime by lazy {
        runtimeCreated = true
        Gecko.createRuntime(this, activeLevel)
    }
    val net by lazy { Net(runtime) }
    val isolation by lazy { Isolation(runtime) { page, onion -> onOnionLocation?.invoke(page, onion) } }
    var onOnionLocation: ((String, String) -> Unit)? = null

    fun startTor() {
        tor.start(settings.network(), settings.effectiveMode()) { Geo.install(this, BuildConfig.BUILD_TIME) }
    }

    /** In AUTO mode, remember which transport worked so the next launch starts there. */
    fun onTorReady() {
        if (settings.connectionMode == ConnectionMode.AUTO) {
            settings.autoStep = ConnectionMode.autoChain.indexOf(tor.mode).coerceAtLeast(0)
        }
    }

    /** Moves AUTO to the next transport. Returns false when there is nothing left to try. */
    fun advanceAutoTransport(): Boolean {
        if (settings.connectionMode != ConnectionMode.AUTO) return false
        val next = ConnectionMode.autoChain.indexOf(tor.mode) + 1
        if (next !in ConnectionMode.autoChain.indices) return false
        settings.autoStep = next
        return true
    }

    fun setKeepAlive(enabled: Boolean) {
        settings.keepAlive = enabled
        val intent = Intent(this, KeepAliveService::class.java)
        if (enabled) ContextCompat.startForegroundService(this, intent) else stopService(intent)
    }

    /** Clears all web data and terminates the process, including tor and the local proxy. */
    fun panic() {
        stopService(Intent(this, KeepAliveService::class.java))
        val exit = Runnable { Process.killProcess(Process.myPid()) }
        if (!runtimeCreated) return exit.run()
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
            .accept({ exit.run() }, { exit.run() })
        Handler(Looper.getMainLooper()).postDelayed(exit, 1_500)
    }
}
