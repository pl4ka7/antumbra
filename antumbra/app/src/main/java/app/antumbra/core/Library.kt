package app.antumbra.core

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A container: tabs in different identities never share cookies, storage or circuits. */
data class Identity(val id: String, val name: String, val color: Int) {
    val contextId get() = "identity-$id"

    companion object {
        val PALETTE = listOf(
            Color(0xFFFF7A4D), Color(0xFF5AA9FF), Color(0xFF4CC38A),
            Color(0xFFB3A7FF), Color(0xFFF2B84B), Color(0xFFFF6B9A),
        )

        fun color(index: Int) = PALETTE[index.mod(PALETTE.size)]
    }
}

class Identities(private val settings: Settings) {

    fun load(defaultNames: List<String>): List<Identity> {
        val stored = runCatching {
            val array = JSONArray(settings.identities)
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                Identity(item.getString("id"), item.getString("name"), item.optInt("color"))
            }
        }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) return stored
        val defaults = defaultNames.mapIndexed { index, name -> Identity(UUID.randomUUID().toString().take(8), name, index) }
        save(defaults)
        return defaults
    }

    fun save(identities: List<Identity>) {
        settings.identities = JSONArray(
            identities.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color) }
        ).toString()
    }

    fun create(name: String, color: Int) = Identity(UUID.randomUUID().toString().take(8), name.trim(), color)
}

data class Bookmark(val title: String, val url: String, val added: Long)

/** Bookmarks encrypted at rest with the Keystore-backed [SecretStore]. */
class Vault(private val secrets: SecretStore) {

    fun load(): List<Bookmark> = runCatching {
        val array = JSONArray(secrets.get(KEY) ?: return emptyList())
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            Bookmark(item.getString("title"), item.getString("url"), item.optLong("added"))
        }
    }.getOrDefault(emptyList())

    fun save(bookmarks: List<Bookmark>) {
        secrets.put(
            KEY,
            JSONArray(bookmarks.map { JSONObject().put("title", it.title).put("url", it.url).put("added", it.added) }).toString(),
        )
    }

    private companion object {
        const val KEY = "vault"
    }
}

data class OnionSite(val category: String, val name: String, val url: String)

/** Verified onion addresses bundled from the Real-World Onion Sites list. */
object OnionDirectory {
    fun load(context: Context): List<OnionSite> = runCatching {
        val json = context.assets.open("directory/onions.json").bufferedReader().use { JSONObject(it.readText()) }
        val sites = json.getJSONArray("sites")
        List(sites.length()) { i ->
            val item = sites.getJSONObject(i)
            OnionSite(item.getString("category"), item.getString("name"), item.getString("url"))
        }
    }.getOrDefault(emptyList())
}
