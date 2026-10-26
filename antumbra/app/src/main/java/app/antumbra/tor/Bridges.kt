package app.antumbra.tor

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Built-in bridge lines published by the Tor Project.
 * Ships with a snapshot in assets and refreshes it over Tor (Moat API) once Tor is up.
 */
class Bridges(private val context: Context) {

    private val updated get() = File(context.noBackupFilesDir, "builtin-bridges.json")

    fun forTransport(key: String): List<String> = runCatching {
        val json = JSONObject(read())
        val array = json.optJSONArray(key) ?: return emptyList()
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    /** Stores a fresh list if it parses and contains at least one obfs4 bridge. */
    fun store(json: String): Boolean = runCatching {
        val parsed = JSONObject(json)
        require((parsed.optJSONArray("obfs4")?.length() ?: 0) > 0)
        updated.writeText(json)
    }.isSuccess

    val isStale: Boolean
        get() = !updated.exists() || System.currentTimeMillis() - updated.lastModified() > WEEK_MS

    private fun read(): String =
        if (updated.exists()) updated.readText()
        else context.assets.open("bridges/builtin.json").bufferedReader().use { it.readText() }

    companion object {
        const val MOAT_BUILTIN = "https://bridges.torproject.org/moat/circumvention/builtin"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /** Parses "key=value" arguments from a bridge line. */
        fun args(line: String): Map<String, String> = line.split(' ')
            .filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
    }
}
