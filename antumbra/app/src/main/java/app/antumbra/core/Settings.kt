package app.antumbra.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

enum class SecurityLevel { STANDARD, SAFER, SAFEST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How tor reaches the network. AUTO walks [ConnectionMode.autoChain] until one bootstraps. */
enum class ConnectionMode {
    AUTO, DIRECT, SNOWFLAKE, OBFS4, MEEK, CUSTOM;

    companion object {
        val autoChain = listOf(DIRECT, SNOWFLAKE, OBFS4, MEEK)
    }
}

enum class SearchEngine(
    val label: String,
    val onion: Boolean,
    val home: String,
    private val query: String,
    private val queryNoScript: String = query,
) {
    DUCKDUCKGO(
        "DuckDuckGo", true,
        "http://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/",
        "http://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=",
        "http://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/html/?q=",
    ),
    BRAVE(
        "Brave Search", true,
        "https://search.brave4u7jddbv7cyviptqjc7jusxh72uik7zt6adtckl5f4nwy2v72qd.onion/",
        "https://search.brave4u7jddbv7cyviptqjc7jusxh72uik7zt6adtckl5f4nwy2v72qd.onion/search?q=",
    ),
    AHMIA(
        "Ahmia", true,
        "http://juhanurmihxlp77nkq76byazcldy2hlmovfu2epvl5ankdibsot4csyd.onion/",
        "http://juhanurmihxlp77nkq76byazcldy2hlmovfu2epvl5ankdibsot4csyd.onion/search/?q=",
    ),
    MOJEEK("Mojeek", false, "https://www.mojeek.com/", "https://www.mojeek.com/search?q="),
    STARTPAGE("Startpage", false, "https://www.startpage.com/", "https://www.startpage.com/do/search?q=");

    fun searchUrl(q: String, scripts: Boolean) = (if (scripts) query else queryNoScript) + Uri.encode(q)
    fun formAction(scripts: Boolean) = (if (scripts) query else queryNoScript).substringBefore('?')
    fun formParam() = query.substringAfter('?').substringBefore('=')
}

enum class AiProvider { OPENROUTER, CUSTOM }

val EXIT_COUNTRIES = listOf(
    "", "at", "bg", "ca", "ch", "cz", "de", "dk", "es", "fi", "fr", "gb",
    "is", "it", "jp", "lu", "lv", "md", "nl", "no", "pl", "ro", "se", "sg", "us",
)

data class NetworkConfig(
    val mode: ConnectionMode,
    val customBridges: List<String>,
    val exitCountry: String,
    val upstreamProxy: String,
)

class Settings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var securityLevel by enumPref("security_level", SecurityLevel.SAFER)
    var theme by enumPref("theme", ThemeMode.SYSTEM)
    var searchEngine by enumPref("search_engine", SearchEngine.DUCKDUCKGO)
    var connectionMode by enumPref("connection_mode", ConnectionMode.AUTO)
    var customBridges by stringPref("custom_bridges")
    var exitCountry by stringPref("exit_country")
    var upstreamProxy by stringPref("upstream_proxy")
    var preferOnion by boolPref("prefer_onion", false)
    var keepAlive by boolPref("keep_alive", false)
    var appLock by boolPref("app_lock", false)
    var discreetIcon by boolPref("discreet_icon", false)
    var onboarded by boolPref("onboarded", false)
    var identities by stringPref("identities")

    /** Minutes in the background before tabs and data are wiped; -1 = never, 0 = on leaving. */
    var autoWipeMinutes by intPref("auto_wipe", -1)

    /** Index in [ConnectionMode.autoChain] that last bootstrapped, used by AUTO. */
    var autoStep by intPref("auto_step", 0)

    var aiProvider by enumPref("ai_provider", AiProvider.OPENROUTER)
    var aiModel by stringPref("ai_model", "openrouter/auto")
    var aiBaseUrl by stringPref("ai_base_url")
    var aiSystemPrompt by stringPref("ai_system_prompt")
    var aiZeroRetention by boolPref("ai_zdr", true)
    var aiRememberKey by boolPref("ai_remember_key", false)

    fun network(): NetworkConfig = NetworkConfig(
        mode = connectionMode,
        customBridges = parseBridgeLines(customBridges),
        exitCountry = exitCountry,
        upstreamProxy = upstreamProxy,
    )

    /** The concrete transport used for this launch. */
    fun effectiveMode(): ConnectionMode =
        if (connectionMode == ConnectionMode.AUTO) {
            ConnectionMode.autoChain.getOrElse(autoStep) { ConnectionMode.DIRECT }
        } else connectionMode

    private fun <T : Enum<T>> enumPref(key: String, default: T) = Pref(
        get = { prefs.getString(key, null)?.let { name -> default.declaringJavaClass.enumConstants?.firstOrNull { it.name == name } } ?: default },
        set = { prefs.edit(commit = true) { putString(key, it.name) } },
    )

    private fun stringPref(key: String, default: String = "") = Pref(
        get = { prefs.getString(key, default) ?: default },
        set = { prefs.edit(commit = true) { putString(key, it) } },
    )

    private fun boolPref(key: String, default: Boolean) = Pref(
        get = { prefs.getBoolean(key, default) },
        set = { prefs.edit(commit = true) { putBoolean(key, it) } },
    )

    private fun intPref(key: String, default: Int) = Pref(
        get = { prefs.getInt(key, default) },
        set = { prefs.edit(commit = true) { putInt(key, it) } },
    )

    class Pref<T>(private val get: () -> T, private val set: (T) -> Unit) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = get()
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) = set(value)
    }

    companion object {
        val TRANSPORTS = setOf("obfs4", "snowflake", "webtunnel", "meek_lite")

        fun parseBridgeLines(text: String): List<String> = text.lines()
            .map { it.trim().removePrefix("Bridge ").removePrefix("bridge ").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.substringBefore(' ') in TRANSPORTS }

        /** Returns the torrc directive and host:port for a proxy URL, or null if unsupported. */
        fun parseUpstream(url: String): Pair<String, String>? {
            if (url.isBlank()) return null
            val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            if (uri.port !in 1..65535) return null
            val directive = when (uri.scheme?.lowercase()) {
                "socks5" -> "Socks5Proxy"
                "socks4" -> "Socks4Proxy"
                "https", "http" -> "HTTPSProxy"
                else -> return null
            }
            return directive to "$host:${uri.port}"
        }
    }
}
