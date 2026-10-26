package app.antumbra

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import app.antumbra.ai.AskViewModel
import app.antumbra.core.ConnectionMode
import app.antumbra.core.ThemeMode
import app.antumbra.system.AppLock
import app.antumbra.ui.ask.AskScreen
import app.antumbra.ui.boot.ConnectingScreen
import app.antumbra.ui.boot.WelcomeScreen
import app.antumbra.ui.browser.BrowserScreen
import app.antumbra.ui.library.LibraryScreen
import app.antumbra.ui.lock.LockScreen
import app.antumbra.ui.reader.ReaderScreen
import app.antumbra.ui.settings.SettingsPage
import app.antumbra.ui.settings.SettingsScreen
import app.antumbra.ui.theme.AntumbraTheme
import app.antumbra.ui.theme.isDark
import app.antumbra.web.Article
import app.antumbra.web.BrowserViewModel

private sealed interface Screen {
    data object Browser : Screen
    data object Ask : Screen
    data object Library : Screen
    data class Preferences(val page: SettingsPage, val returnTo: Screen) : Screen
}

class MainActivity : FragmentActivity() {

    private val browser: BrowserViewModel by viewModels()
    private val ask: AskViewModel by viewModels()
    private val app get() = application as AntumbraApp

    private var screen by mutableStateOf<Screen>(Screen.Browser)
    private var theme by mutableStateOf(ThemeMode.SYSTEM)
    private var onboarded by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var autoExhausted by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        theme = app.settings.theme
        onboarded = app.settings.onboarded
        locked = app.settings.appLock && AppLock.available(this)
        if (onboarded) connect()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val dark = isDark(theme)
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                browser.darkHome = dark
            }
            AntumbraTheme(dark) {
                val tor by browser.tor.state.collectAsState()
                val current = screen
                val article = browser.article
                when {
                    locked -> LockScreen(onUnlock = ::unlock)
                    current is Screen.Preferences -> Preferences(current)
                    !onboarded -> WelcomeScreen(
                        onConnect = ::connect,
                        onConfigure = { screen = Screen.Preferences(SettingsPage.CONNECTION, Screen.Browser) },
                    )
                    !browser.ready -> ConnectingScreen(
                        state = tor,
                        mode = browser.tor.mode,
                        autoMode = app.settings.connectionMode == ConnectionMode.AUTO && !autoExhausted,
                        onStalled = ::advanceTransport,
                        onRetry = ::restart,
                        onConfigure = { screen = Screen.Preferences(SettingsPage.CONNECTION, Screen.Browser) },
                    )
                    article != null && current == Screen.Browser -> ReaderScreen(
                        article = article,
                        canSummarize = true,
                        onSummarize = { summarize(article) },
                        onClose = browser::closeReader,
                    )
                    else -> AnimatedContent(
                        targetState = current,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            Screen.Ask -> AskScreen(
                                vm = ask,
                                onBack = { screen = Screen.Browser },
                                onOpenLink = ::openInTab,
                                onSettings = { screen = Screen.Preferences(SettingsPage.ASSISTANT, Screen.Ask) },
                            )
                            Screen.Library -> LibraryScreen(
                                vm = browser,
                                onOpen = ::openInTab,
                                onBack = { screen = Screen.Browser },
                            )
                            else -> BrowserScreen(
                                vm = browser,
                                onAsk = { screen = Screen.Ask },
                                onLibrary = { screen = Screen.Library },
                                onSettings = { identities ->
                                    screen = Screen.Preferences(
                                        if (identities) SettingsPage.IDENTITIES else SettingsPage.ROOT,
                                        Screen.Browser,
                                    )
                                },
                                onPanic = { app.panic() },
                                onRestart = ::restart,
                            )
                        }
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                screen != Screen.Browser -> screen = Screen.Browser
                browser.article != null -> browser.closeReader()
                browser.find != null -> browser.clearFind()
                !browser.back() -> moveTaskToBack(true)
            }
        }
    }

    @Composable
    private fun Preferences(target: Screen.Preferences) {
        SettingsScreen(
            browser = browser,
            ask = ask,
            settings = app.settings,
            startPage = target.page,
            torRunning = onboarded,
            onTheme = { theme = it },
            onKeepAlive = app::setKeepAlive,
            onOpenLink = ::openInTab,
            onRestart = { browser.wipe { restart() } },
            onClose = { screen = target.returnTo },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Links opened from other apps (default browser) or text shared to Antumbra. */
    private fun handleIntent(intent: Intent?) {
        val target = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("https?://\\S+").find(text)?.value ?: text
            }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra("query")
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return
        screen = Screen.Browser
        browser.openExternal(target)
    }

    private fun summarize(article: Article) {
        val text = article.plainText.take(24_000)
        ask.sendWithContext(
            display = getString(R.string.reader_summary_request, article.title.ifBlank { article.site }),
            content = getString(R.string.reader_summary_prompt) + "\n\n" + article.title + "\n" + article.url + "\n\n" + text,
        )
        browser.closeReader()
        screen = Screen.Ask
    }

    private fun connect() {
        if (!app.settings.onboarded) app.settings.onboarded = true
        onboarded = true
        app.startTor()
        if (app.settings.keepAlive) app.setKeepAlive(true)
    }

    private fun advanceTransport() {
        if (app.advanceAutoTransport()) restart() else autoExhausted = true
    }

    private fun openInTab(url: String) {
        screen = Screen.Browser
        if (browser.ready) browser.newTab(browser.resolve(url))
    }

    private fun unlock() {
        AppLock.prompt(this, getString(R.string.lock_prompt_title)) { locked = false }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        val wipeAfter = app.settings.autoWipeMinutes
        if (wipeAfter >= 0 && browser.ready && away >= wipeAfter * 60_000L) {
            browser.resetSession()
            ask.clear()
            screen = Screen.Browser
        }
        if (away > AppLock.TIMEOUT_MS && app.settings.appLock && AppLock.available(this)) locked = true
    }

    /** tor runs once per process, so applying network changes means starting a fresh process. */
    private fun restart() {
        startActivity(Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java)))
        Runtime.getRuntime().exit(0)
    }
}
