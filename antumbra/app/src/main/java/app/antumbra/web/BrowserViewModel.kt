package app.antumbra.web

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.antumbra.AntumbraApp
import app.antumbra.R
import app.antumbra.core.Bookmark
import app.antumbra.core.Domains
import app.antumbra.core.Identities
import app.antumbra.core.Identity
import app.antumbra.core.SearchEngine
import app.antumbra.core.SecurityLevel
import app.antumbra.core.Vault
import app.antumbra.tor.Circuit
import app.antumbra.tor.TorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

const val CHECK_URL = "https://check.torproject.org/"
const val HOME_URL = "about:blank"

class Tab(val id: Long, val session: GeckoSession, val identity: Identity) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var secure by mutableStateOf(false)
    var onionAlternative by mutableStateOf<String?>(null)
    var javascript by mutableStateOf(true)
    var desktop by mutableStateOf(false)
    val blocked = mutableStateListOf<String>()

    val isHome get() = url.isBlank() || url == HOME_URL
    val host: String get() = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
    val isOnion get() = host.endsWith(".onion")
}

sealed class WebPrompt {
    val result = GeckoResult<PromptDelegate.PromptResponse>()
    abstract fun dismiss(): PromptDelegate.PromptResponse

    class Alert(val prompt: PromptDelegate.AlertPrompt) : WebPrompt() {
        override fun dismiss() = prompt.dismiss()
    }

    class Confirm(val prompt: PromptDelegate.ButtonPrompt) : WebPrompt() {
        override fun dismiss() = prompt.dismiss()
    }

    class Input(val prompt: PromptDelegate.TextPrompt) : WebPrompt() {
        override fun dismiss() = prompt.dismiss()
    }

    class Choice(val prompt: PromptDelegate.ChoicePrompt) : WebPrompt() {
        override fun dismiss() = prompt.dismiss()
    }
}

data class CircuitView(val host: String, val loading: Boolean, val circuit: Circuit?)

class PendingDownload(val response: WebResponse, val fileName: String, val mimeType: String, val size: Long)

data class FindState(val query: String, val current: Int, val total: Int)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AntumbraApp
    val tor = app.tor
    val settings = app.settings
    val level: SecurityLevel get() = app.activeLevel
    private val scripts get() = level != SecurityLevel.SAFEST
    private val runtime get() = app.runtime
    private val identityStore = Identities(settings)
    private val vaultStore = Vault(app.secrets)

    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableLongStateOf(-1L)
        private set
    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }

    val identities = mutableStateListOf<Identity>()
    var activeIdentity by mutableStateOf<Identity?>(null)
        private set
    val vault = mutableStateListOf<Bookmark>()

    var engine by mutableStateOf(settings.searchEngine)
        private set
    var darkHome by mutableStateOf(true)
    var prompt by mutableStateOf<WebPrompt?>(null)
        private set
    var paymentUri by mutableStateOf<String?>(null)
        private set
    var circuit by mutableStateOf<CircuitView?>(null)
        private set
    var download by mutableStateOf<PendingDownload?>(null)
        private set
    var downloadProgress by mutableStateOf<Float?>(null)
        private set
    var find by mutableStateOf<FindState?>(null)
        private set
    var article by mutableStateOf<Article?>(null)
        private set
    var extracting by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set

    /** Trackers blocked since the app started. */
    var blockedTotal by mutableIntStateOf(0)
        private set
    /** Distinct sites visited this session, each on its own circuit. */
    var sitesIsolated by mutableIntStateOf(0)
        private set
    private val sitesSeen = HashSet<String>()

    val isolationActive get() = app.isolation.active

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    private var nextId = 0L
    private var pendingExternal: String? = null

    init {
        identities += identityStore.load(
            listOf(str(R.string.identity_personal), str(R.string.identity_work), str(R.string.identity_research))
        )
        activeIdentity = identities.first()
        vault += vaultStore.load()
        app.onOnionLocation = ::onOnionLocation
        viewModelScope.launch {
            tor.state.first { it is TorState.Ready }
            app.onTorReady()
            runtime.storageController.clearData(StorageController.ClearFlags.ALL).await()
            app.isolation.install()
            ready = true
            val external = pendingExternal
            pendingExternal = null
            if (tabs.isEmpty()) newTab(external?.let(::resolve) ?: HOME_URL)
            launch { tor.refreshBridges { url -> runCatching { app.net.text(url) }.getOrNull() } }
        }
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    // Tabs

    fun newTab(url: String? = HOME_URL, identity: Identity? = activeIdentity, select: Boolean = true, open: Boolean = true): Tab {
        val owner = identity ?: identities.first()
        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(true)
                .useTrackingProtection(true)
                .allowJavascript(scripts)
                .contextId(owner.contextId)
                .build()
        )
        val tab = Tab(nextId++, session, owner).apply { javascript = scripts }
        attach(tab)
        if (open) session.open(runtime)
        app.isolation.attach(session)
        url?.let { session.loadUri(it) }
        tabs += tab
        if (select) select(tab)
        return tab
    }

    fun select(tab: Tab) {
        currentId = tab.id
        activeIdentity = tab.identity
        clearFind()
    }

    fun selectRelative(step: Int) {
        val index = tabs.indexOfFirst { it.id == currentId }
        if (index < 0 || tabs.size < 2) return
        select(tabs[(index + step).mod(tabs.size)])
    }

    fun close(tab: Tab) {
        tab.session.close()
        tabs.remove(tab)
        when {
            tabs.isEmpty() -> newTab()
            currentId == tab.id -> select(tabs.last())
        }
    }

    fun closeAll() {
        tabs.toList().forEach { it.session.close() }
        tabs.clear()
        newTab()
    }

    /** Opens a link handed over by another app. */
    fun openExternal(url: String) {
        if (ready) newTab(resolve(url)) else pendingExternal = url
    }

    fun load(input: String) = current?.session?.loadUri(resolve(input))
    fun goHome() = current?.session?.loadUri(HOME_URL)
    fun reload() = current?.session?.reload()
    fun forward() = current?.session?.goForward()
    fun back(): Boolean = current?.takeIf { it.canBack }?.let { it.session.goBack(); true } ?: false

    fun setSearchEngine(value: SearchEngine) {
        settings.searchEngine = value
        engine = value
    }

    fun resolve(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return HOME_URL
        if (' ' !in text && ("://" in text || text.startsWith("about:"))) return text
        if (' ' !in text && '.' in text) {
            val host = text.substringBefore('/').substringBefore(':').lowercase()
            return if (host.endsWith(".onion")) "http://$text" else "https://$text"
        }
        return engine.searchUrl(text, scripts)
    }

    // Identities

    fun useIdentity(identity: Identity) {
        activeIdentity = identity
        tabs.lastOrNull { it.identity.id == identity.id }?.let(::select) ?: newTab(identity = identity)
    }

    fun addIdentity(name: String) {
        if (name.isBlank()) return
        identities += identityStore.create(name, identities.size)
        identityStore.save(identities)
    }

    fun renameIdentity(identity: Identity, name: String) {
        val index = identities.indexOfFirst { it.id == identity.id }
        if (index < 0 || name.isBlank()) return
        identities[index] = identity.copy(name = name.trim())
        identityStore.save(identities)
    }

    /** Closes the identity's tabs and clears its cookies and storage. */
    fun wipeIdentity(identity: Identity) {
        tabs.filter { it.identity.id == identity.id }.forEach { it.session.close(); tabs.remove(it) }
        runtime.storageController.clearDataForSessionContext(identity.contextId)
        if (tabs.isEmpty()) newTab(identity = identity)
        else if (tabs.none { it.id == currentId }) select(tabs.last())
        toast(str(R.string.toast_identity_wiped, identity.name))
    }

    fun deleteIdentity(identity: Identity) {
        if (identities.size <= 1) return
        wipeIdentity(identity)
        identities.removeAll { it.id == identity.id }
        identityStore.save(identities)
        tabs.filter { it.identity.id == identity.id }.forEach { it.session.close(); tabs.remove(it) }
        if (activeIdentity?.id == identity.id) activeIdentity = identities.first()
        if (tabs.isEmpty()) newTab()
        else if (tabs.none { it.id == currentId }) select(tabs.last())
    }

    // Vault

    fun isSaved(url: String) = vault.any { it.url == url }

    fun toggleSaved(tab: Tab) {
        if (tab.isHome) return
        if (isSaved(tab.url)) {
            vault.removeAll { it.url == tab.url }
            toast(str(R.string.toast_vault_removed))
        } else {
            vault.add(0, Bookmark(tab.title.ifBlank { tab.host }, tab.url, System.currentTimeMillis()))
            toast(str(R.string.toast_vault_saved))
        }
        vaultStore.save(vault)
    }

    fun removeSaved(bookmark: Bookmark) {
        vault.remove(bookmark)
        vaultStore.save(vault)
    }

    // Shield

    fun setJavascript(tab: Tab, enabled: Boolean) {
        tab.session.settings.allowJavascript = enabled
        tab.javascript = enabled
        tab.session.reload()
    }

    fun setDesktop(tab: Tab, enabled: Boolean) {
        tab.session.settings.userAgentMode =
            if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        tab.session.settings.viewportMode =
            if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        tab.desktop = enabled
        tab.session.reload()
    }

    fun openOnionAlternative(tab: Tab) {
        tab.onionAlternative?.let { tab.session.loadUri(it) }
    }

    private fun onOnionLocation(page: String, onion: String) {
        val tab = tabs.firstOrNull { it.url == page } ?: return
        tab.onionAlternative = onion
        if (settings.preferOnion) tab.session.loadUri(onion)
    }

    fun loadCircuit() {
        val host = current?.takeUnless { it.isHome }?.host.orEmpty()
        circuit = CircuitView(host, host.isNotEmpty(), null)
        if (host.isEmpty()) return
        viewModelScope.launch { circuit = CircuitView(host, false, tor.circuitFor(host)) }
    }

    /** Equivalent of Tor Browser's "New circuit for this site". */
    fun newCircuitForSite() {
        val tab = current?.takeUnless { it.isHome } ?: return
        val domain = Domains.base(tab.host)
        app.isolation.rotate(domain)
        circuit = null
        tab.session.reload()
        toast(str(R.string.toast_new_circuit, domain))
    }

    // Find in page

    fun startFind() {
        find = FindState("", 0, 0)
    }

    fun find(query: String, backwards: Boolean = false) {
        val session = current?.session ?: return
        if (query.isEmpty()) {
            session.finder.clear()
            find = FindState("", 0, 0)
            return
        }
        val flags = if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else 0
        viewModelScope.launch {
            val result = session.finder.find(query, flags).await()
            find = FindState(query, result?.current ?: 0, result?.total ?: 0)
        }
    }

    fun clearFind() {
        current?.session?.finder?.clear()
        find = null
    }

    // Reader

    fun openReader() {
        val tab = current?.takeUnless { it.isHome } ?: return
        extracting = true
        viewModelScope.launch {
            val result = app.isolation.extract(tab.session)
            extracting = false
            if (result == null || result.blocks.isEmpty()) toast(str(R.string.toast_reader_unavailable))
            else article = result
        }
    }

    fun closeReader() {
        article = null
    }

    // Downloads

    fun cancelDownload() {
        download?.let { runCatching { it.response.body?.close() } }
        download = null
    }

    fun saveDownload(target: Uri) {
        val pending = download ?: return
        download = null
        downloadProgress = 0f
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val input = pending.response.body ?: error("empty")
                    getApplication<Application>().contentResolver.openOutputStream(target).use { output ->
                        requireNotNull(output)
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (pending.size > 0) downloadProgress = (copied.toFloat() / pending.size).coerceIn(0f, 1f)
                        }
                    }
                    input.close()
                }.isSuccess
            }
            downloadProgress = null
            toast(str(if (ok) R.string.toast_download_done else R.string.toast_download_failed, pending.fileName))
        }
    }

    private fun fileName(response: WebResponse): String {
        val disposition = response.headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value.orEmpty()
        val encoded = Regex("filename\\*=(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)
        val plain = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)
        val fromUrl = Uri.parse(response.uri).lastPathSegment
        val name = encoded?.let { runCatching { URLDecoder.decode(it.trim('"'), "UTF-8") }.getOrNull() } ?: plain ?: fromUrl
        return name?.replace(Regex("[/\\\\:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() } ?: "download"
    }

    // Session lifecycle

    fun newIdentity() = resetSession(R.string.toast_new_identity)

    /** Closes every tab, clears all web data and asks tor for fresh circuits. */
    fun resetSession(message: Int? = null) {
        viewModelScope.launch {
            tabs.toList().forEach { it.session.close() }
            tabs.clear()
            clearFind()
            article = null
            runtime.storageController.clearData(StorageController.ClearFlags.ALL).await()
            app.isolation.reset()
            val rotated = tor.newIdentity()
            newTab()
            if (message != null) toast(str(if (rotated) message else R.string.toast_new_identity_limited))
        }
    }

    fun wipe(onDone: () -> Unit) {
        val done = AtomicBoolean(false)
        val finish = { if (done.compareAndSet(false, true)) onDone() }
        tabs.toList().forEach { it.session.close() }
        tabs.clear()
        viewModelScope.launch {
            runCatching { runtime.storageController.clearData(StorageController.ClearFlags.ALL).await() }
            finish()
        }
        viewModelScope.launch {
            delay(1_500)
            finish()
        }
    }

    fun dismissPayment() {
        paymentUri = null
    }

    private fun enqueue(next: WebPrompt): GeckoResult<PromptDelegate.PromptResponse> {
        prompt?.let { it.result.complete(it.dismiss()) }
        prompt = next
        return next.result
    }

    fun answer(response: PromptDelegate.PromptResponse) {
        prompt?.result?.complete(response)
        prompt = null
    }

    private fun attach(tab: Tab) {
        val session = tab.session

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.progress = 5
                tab.blocked.clear()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                tab.progress = 100
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                tab.secure = securityInfo.isSecure
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                val host = runCatching { Uri.parse(event.uri).host }.getOrNull() ?: return
                blockedTotal++
                if (host !in tab.blocked) tab.blocked += host
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val next = url.orEmpty()
                val host = Uri.parse(next).host
                if (host != Uri.parse(tab.url).host) tab.onionAlternative = null
                if (!host.isNullOrBlank() && sitesSeen.add(Domains.base(host))) sitesIsolated = sitesSeen.size
                tab.url = next
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canForward = canGoForward
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                val scheme = request.uri.substringBefore(':').lowercase()
                return when {
                    isWebScheme(request.uri) -> GeckoResult.allow()
                    scheme in PAYMENT_SCHEMES -> {
                        if (request.hasUserGesture) paymentUri = request.uri
                        GeckoResult.deny()
                    }
                    else -> {
                        toast(str(R.string.toast_blocked_scheme, scheme))
                        GeckoResult.deny()
                    }
                }
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!isWebScheme(uri)) return null
                return GeckoResult.fromValue(newTab(url = null, identity = tab.identity, open = false).session)
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? =
                GeckoResult.fromValue(errorPage(uri.orEmpty(), error))
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onCloseRequest(session: GeckoSession) = close(tab)

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                download?.let { runCatching { it.response.body?.close() } }
                response.setReadTimeoutMillis(10 * 60_000L)
                val size = response.headers.entries.firstOrNull { it.key.equals("content-length", true) }?.value?.toLongOrNull() ?: -1L
                val mime = response.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
                    ?.substringBefore(';')?.trim()?.ifBlank { null } ?: "application/octet-stream"
                download = PendingDownload(response, fileName(response), mime, size)
            }

            override fun onCrash(session: GeckoSession) = recover(tab)
            override fun onKill(session: GeckoSession) = recover(tab)
        }

        session.permissionDelegate = object : PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: PermissionDelegate.Callback,
            ) = callback.reject()

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? = GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out PermissionDelegate.MediaSource>?,
                audio: Array<out PermissionDelegate.MediaSource>?,
                callback: PermissionDelegate.MediaCallback,
            ) = callback.reject()
        }

        session.promptDelegate = object : PromptDelegate {
            override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
                enqueue(WebPrompt.Alert(prompt))

            override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) =
                enqueue(WebPrompt.Confirm(prompt))

            override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) =
                enqueue(WebPrompt.Input(prompt))

            override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) =
                enqueue(WebPrompt.Choice(prompt))
        }
    }

    private fun recover(tab: Tab) {
        if (tab.session.isOpen) tab.session.close()
        tab.session.open(runtime)
        tab.session.loadUri(tab.url.ifBlank { HOME_URL })
    }

    private fun isWebScheme(uri: String) = when (uri.substringBefore(':').lowercase()) {
        "http", "https", "data", "blob" -> true
        "about" -> !uri.startsWith("about:config")
        else -> false
    }

    private fun errorPage(uri: String, error: WebRequestError): String {
        val (title, detail) = when (error.code) {
            WebRequestError.ERROR_PROXY_CONNECTION_REFUSED,
            WebRequestError.ERROR_UNKNOWN_PROXY_HOST -> R.string.error_tor_title to R.string.error_tor_detail
            WebRequestError.ERROR_UNKNOWN_HOST -> R.string.error_host_title to R.string.error_host_detail
            WebRequestError.ERROR_NET_TIMEOUT -> R.string.error_timeout_title to R.string.error_timeout_detail
            WebRequestError.ERROR_CONNECTION_REFUSED -> R.string.error_refused_title to R.string.error_refused_detail
            WebRequestError.ERROR_SECURITY_BAD_CERT,
            WebRequestError.ERROR_SECURITY_SSL -> R.string.error_cert_title to R.string.error_cert_detail
            WebRequestError.ERROR_HTTPS_ONLY -> R.string.error_http_title to R.string.error_http_detail
            else -> R.string.error_generic_title to R.string.error_generic_detail
        }
        val dark = darkHome
        val html = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;padding:22vh 28px;background:${if (dark) "#15181C" else "#F6F4EF"};color:${if (dark) "#ECE9E2" else "#15171A"};font:16px/1.55 system-ui,sans-serif}
h1{font-size:24px;font-weight:600;margin:0 0 10px}p{margin:0;color:${if (dark) "#BCC2CA" else "#5A6068"}}
code{display:block;margin-top:28px;font:12px monospace;color:${if (dark) "#6E757E" else "#8A9099"};word-break:break-all}</style></head>
<body><h1>${esc(str(title))}</h1><p>${esc(str(detail))}</p><code>${esc(uri)}</code></body></html>"""
        return "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    override fun onCleared() {
        app.onOnionLocation = null
    }

    companion object {
        val PAYMENT_SCHEMES = setOf(
            "bitcoin", "ethereum", "monero", "litecoin", "lightning", "lnurl", "lnurlp", "lnurlw",
            "wc", "solana", "dogecoin", "bitcoincash", "zcash", "tron", "ton",
        )
    }
}
