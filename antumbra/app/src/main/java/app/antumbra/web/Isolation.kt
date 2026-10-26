package app.antumbra.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.WeakHashMap

data class ArticleBlock(val tag: String, val text: String)

data class Article(
    val title: String,
    val site: String,
    val url: String,
    val byline: String,
    val blocks: List<ArticleBlock>,
) {
    val plainText: String get() = blocks.joinToString("\n\n") { it.text }
}

/**
 * Bridge to the built-in extension (assets/extensions/isolation).
 * The background script assigns SOCKS credentials per site and tab and reports Onion-Location
 * headers; the content script extracts readable articles on request.
 */
class Isolation(
    private val runtime: GeckoRuntime,
    private val onOnionLocation: (page: String, onion: String) -> Unit,
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var extension: WebExtension? = null
    private var background: WebExtension.Port? = null
    private val pagePorts = WeakHashMap<GeckoSession, WebExtension.Port>()
    private val pending = HashMap<Int, CompletableDeferred<Article>>()
    private var nextRequest = 0

    suspend fun install(): Boolean = withTimeoutOrNull(5_000) {
        val controller = runtime.webExtensionController
        val installed = controller.ensureBuiltIn(URI, ID).await() ?: return@withTimeoutOrNull false
        controller.setAllowedInPrivateBrowsing(installed, true).await()
        installed.setMessageDelegate(backgroundMessages, NATIVE_APP)
        extension = installed
        true
    } ?: false

    /** Lets the content script of [session] talk to the app. Call for every new session. */
    fun attach(session: GeckoSession) {
        val ext = extension ?: return
        session.webExtensionController.setMessageDelegate(ext, pageMessages(session), NATIVE_APP)
    }

    fun rotate(domain: String) = background?.postMessage(JSONObject().put("type", "rotate").put("domain", domain))

    fun reset() = background?.postMessage(JSONObject().put("type", "reset"))

    /** Extracts the readable article of the page shown in [session], or null if unavailable. */
    suspend fun extract(session: GeckoSession): Article? {
        val port = pagePorts[session] ?: return null
        val id = nextRequest++
        val result = CompletableDeferred<Article>()
        pending[id] = result
        port.postMessage(JSONObject().put("type", "extract").put("id", id))
        return withTimeoutOrNull(8_000) { result.await() }.also { pending.remove(id) }
    }

    private fun parseArticle(json: JSONObject): Article {
        val array = json.optJSONArray("blocks")
        val blocks = if (array == null) emptyList() else List(array.length()) { index ->
            val block = array.getJSONObject(index)
            ArticleBlock(block.optString("t"), block.optString("x"))
        }
        return Article(
            title = json.optString("title"),
            site = json.optString("site"),
            url = json.optString("url"),
            byline = json.optString("byline"),
            blocks = blocks,
        )
    }

    private val backgroundPort = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = message as? JSONObject ?: return
            when (json.optString("type")) {
                "ready" -> _active.value = true
                "onion" -> onOnionLocation(json.optString("page"), json.optString("onion"))
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (background === port) {
                background = null
                _active.value = false
            }
        }
    }

    private val backgroundMessages = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            if (port.sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION) return
            background = port
            port.setDelegate(backgroundPort)
        }

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? = null
    }

    private fun pageMessages(session: GeckoSession) = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            pagePorts[session] = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    if (json.optString("type") == "article") {
                        pending[json.optInt("id", -1)]?.complete(parseArticle(json))
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (pagePorts[session] === port) pagePorts.remove(session)
                }
            })
        }

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? = null
    }

    private companion object {
        const val URI = "resource://android/assets/extensions/isolation/"
        const val ID = "isolation@antumbra.app"
        const val NATIVE_APP = "antumbra"
    }
}
