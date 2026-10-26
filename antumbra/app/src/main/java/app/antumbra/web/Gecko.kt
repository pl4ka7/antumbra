package app.antumbra.web

import android.content.Context
import app.antumbra.core.SecurityLevel
import app.antumbra.tor.TorController
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File
import kotlin.coroutines.resume

/**
 * Builds the single GeckoRuntime. Preferences are applied through a YAML config file at startup,
 * so a security level change takes effect after restart. The proxy fails closed: if tor is not
 * reachable, Gecko never falls back to a direct connection.
 */
object Gecko {

    fun createRuntime(context: Context, level: SecurityLevel): GeckoRuntime {
        val config = File(context.noBackupFilesDir, "gecko.yaml").apply { writeText(yaml(prefs(level))) }

        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .configFilePath(config.absolutePath)
            .javaScriptEnabled(level != SecurityLevel.SAFEST)
            .webFontsEnabled(level == SecurityLevel.STANDARD)
            .remoteDebuggingEnabled(false)
            .consoleOutput(false)
            .aboutConfigEnabled(false)
            .loginAutofillEnabled(false)
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            .contentBlocking(blocking)
            .build()

        return GeckoRuntime.create(context, settings)
    }

    private fun prefs(level: SecurityLevel): Map<String, Any> {
        val prefs = linkedMapOf<String, Any>(
            "network.proxy.type" to 1,
            "network.proxy.socks" to "127.0.0.1",
            "network.proxy.socks_port" to TorController.SOCKS_PORT,
            "network.proxy.socks_version" to 5,
            "network.proxy.socks_remote_dns" to true,
            "network.proxy.socks5_remote_dns" to true,
            "network.proxy.no_proxies_on" to "",
            "network.proxy.allow_hijacking_localhost" to true,
            "network.proxy.failover_direct" to false,
            "network.proxy.allow_bypass" to false,
            "network.dns.disabled" to true,
            "network.trr.mode" to 5,
            "network.http.http3.enable" to false,
            "network.dns.disablePrefetch" to true,
            "network.prefetch-next" to false,
            "network.predictor.enabled" to false,
            "network.http.speculative-parallel-limit" to 0,
            "network.captive-portal-service.enabled" to false,
            "network.connectivity-service.enabled" to false,
            "captivedetect.canonicalURL" to "",
            "browser.send_pings" to false,

            "media.peerconnection.enabled" to false,
            "media.navigator.enabled" to false,
            "geo.enabled" to false,
            "device.sensors.enabled" to false,
            "dom.battery.enabled" to false,
            "dom.netinfo.enabled" to false,
            "dom.gamepad.enabled" to false,
            "dom.vr.enabled" to false,
            "dom.vibrator.enabled" to false,
            "dom.webgpu.enabled" to false,
            "beacon.enabled" to false,
            "dom.push.enabled" to false,
            "dom.webnotifications.enabled" to false,

            "privacy.resistFingerprinting" to true,
            "privacy.resistFingerprinting.letterboxing" to true,
            "privacy.firstparty.isolate" to true,
            "network.http.referer.XOriginTrimmingPolicy" to 2,

            "dom.security.https_only_mode" to true,
            "dom.security.https_only_mode.upgrade_onion" to false,

            "browser.cache.disk.enable" to false,
            "media.cache_size" to 0,
            "toolkit.telemetry.enabled" to false,
            "datareporting.healthreport.uploadEnabled" to false,
            "media.autoplay.default" to 5,
        )
        if (level != SecurityLevel.STANDARD) {
            prefs += mapOf(
                "javascript.options.ion" to false,
                "javascript.options.baselinejit" to false,
                "javascript.options.native_regexp" to false,
                "javascript.options.wasm" to false,
                "webgl.disabled" to true,
                "mathml.disabled" to true,
                "gfx.downloadable_fonts.enabled" to false,
                "gfx.font_rendering.opentype_svg.enabled" to false,
                "media.autoplay.blocking_policy" to 2,
            )
        }
        if (level == SecurityLevel.SAFEST) {
            prefs += mapOf("svg.disabled" to true, "javascript.enabled" to false)
        }
        return prefs
    }

    private fun yaml(prefs: Map<String, Any>) = buildString {
        appendLine("prefs:")
        prefs.forEach { (key, value) -> appendLine("  $key: ${if (value is String) "\"$value\"" else value}") }
    }
}

/** Suspends until the result completes; returns null on error. */
suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { cont ->
    accept({ if (cont.isActive) cont.resume(it) }, { if (cont.isActive) cont.resume(null) })
}
