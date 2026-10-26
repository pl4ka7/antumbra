package app.antumbra.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_APP_LOCALE_SETTINGS
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.antumbra.BuildConfig
import app.antumbra.R
import app.antumbra.ai.AskViewModel
import app.antumbra.core.ConnectionMode
import app.antumbra.core.EXIT_COUNTRIES
import app.antumbra.core.SearchEngine
import app.antumbra.core.SecurityLevel
import app.antumbra.core.Settings
import app.antumbra.core.ThemeMode
import app.antumbra.system.AppLock
import app.antumbra.system.LauncherIcon
import app.antumbra.tor.Geo
import app.antumbra.tor.TorController
import app.antumbra.ui.ask.AiProviderForm
import app.antumbra.ui.ask.ModelSheet
import app.antumbra.ui.boot.modeLabel
import app.antumbra.ui.components.Hint
import app.antumbra.ui.components.IncognitoField
import app.antumbra.ui.components.KeyValueRow
import app.antumbra.ui.components.SectionHeader
import app.antumbra.ui.components.copySensitive
import app.antumbra.ui.theme.MonoSmall
import app.antumbra.ui.theme.AppMono
import app.antumbra.web.BrowserViewModel
import java.util.concurrent.TimeUnit

enum class SettingsPage { ROOT, APPEARANCE, SEARCH, IDENTITIES, PRIVACY, CONNECTION, APPS, ASSISTANT, ABOUT }

@Composable
fun SettingsScreen(
    browser: BrowserViewModel,
    ask: AskViewModel,
    settings: Settings,
    startPage: SettingsPage,
    torRunning: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onKeepAlive: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(startPage) }
    var restartNeeded by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = {
        if (page == SettingsPage.ROOT || page == startPage) {
            onClose()
        } else {
            page = SettingsPage.ROOT
        }
    }
    BackHandler(onBack = back)
    val markRestart: () -> Unit = {
        if (torRunning) {
            restartNeeded = true
        }
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != SettingsPage.ROOT
            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + androidx.compose.animation.fadeOut())
        },
        label = "settings",
    ) { current ->
        Page(
            title = stringResource(titleOf(current)),
            onBack = back,
            restartNeeded = restartNeeded,
            onRestart = onRestart,
        ) {
            when (current) {
                SettingsPage.ROOT -> Root(onOpen = { page = it })
                SettingsPage.APPEARANCE -> Appearance(settings, onTheme)
                SettingsPage.SEARCH -> Search(browser)
                SettingsPage.PRIVACY -> Privacy(settings, onChanged = markRestart)
                SettingsPage.CONNECTION -> Connection(settings, onChanged = markRestart)
                SettingsPage.APPS -> Apps(settings, onKeepAlive)
                SettingsPage.ASSISTANT -> Assistant(ask, settings, onOpenLink)
                SettingsPage.ABOUT -> About(onOpenLink)
                SettingsPage.IDENTITIES -> IdentitiesPage(browser)
            }
        }
    }
}

private fun titleOf(page: SettingsPage) = when (page) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.APPEARANCE -> R.string.settings_appearance
    SettingsPage.SEARCH -> R.string.settings_search
    SettingsPage.PRIVACY -> R.string.settings_privacy
    SettingsPage.CONNECTION -> R.string.settings_connection
    SettingsPage.APPS -> R.string.settings_apps
    SettingsPage.ASSISTANT -> R.string.settings_assistant
    SettingsPage.ABOUT -> R.string.settings_about
    SettingsPage.IDENTITIES -> R.string.settings_identities
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    restartNeeded: Boolean,
    onRestart: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            if (restartNeeded) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_restart_needed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Button(onClick = onRestart) { Text(stringResource(R.string.action_restart)) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            content()
        }
    }
}

@Composable
private fun Root(onOpen: (SettingsPage) -> Unit) {
    Entry(Icons.Outlined.Palette, R.string.settings_appearance, R.string.settings_appearance_detail) { onOpen(SettingsPage.APPEARANCE) }
    Entry(Icons.Outlined.Search, R.string.settings_search, R.string.settings_search_detail) { onOpen(SettingsPage.SEARCH) }
    Entry(Icons.Outlined.Group, R.string.settings_identities, R.string.settings_identities_detail) { onOpen(SettingsPage.IDENTITIES) }
    Entry(Icons.Outlined.Shield, R.string.settings_privacy, R.string.settings_privacy_detail) { onOpen(SettingsPage.PRIVACY) }
    Entry(Icons.Outlined.Router, R.string.settings_connection, R.string.settings_connection_detail) { onOpen(SettingsPage.CONNECTION) }
    Entry(Icons.Outlined.AccountBalanceWallet, R.string.settings_apps, R.string.settings_apps_detail) { onOpen(SettingsPage.APPS) }
    Entry(Icons.Outlined.ChatBubbleOutline, R.string.settings_assistant, R.string.settings_assistant_detail) { onOpen(SettingsPage.ASSISTANT) }
    Entry(Icons.Outlined.Info, R.string.settings_about, R.string.settings_about_detail) { onOpen(SettingsPage.ABOUT) }
}

@Composable
private fun Entry(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun Choice(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Appearance(settings: Settings, onTheme: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(settings.theme) }
    SectionHeader(stringResource(R.string.settings_theme))
    listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    ).forEach { (mode, label) ->
        Choice(stringResource(label), null, theme == mode) {
            theme = mode
            settings.theme = mode
            onTheme(mode)
        }
    }
    if (Build.VERSION.SDK_INT >= 33) {
        SectionHeader(stringResource(R.string.settings_language))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_language_app)) },
            supportingContent = { Text(stringResource(R.string.settings_language_detail)) },
            leadingContent = { Icon(Icons.Outlined.Language, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                context.startActivity(Intent(ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Search(browser: BrowserViewModel) {
    SectionHeader(stringResource(R.string.settings_engine))
    SearchEngine.entries.forEach { engine ->
        Choice(
            engine.label,
            stringResource(if (engine.onion) R.string.engine_onion else R.string.engine_clearnet),
            browser.engine == engine,
        ) { browser.setSearchEngine(engine) }
    }
    Hint(stringResource(R.string.engine_hint))
}

@Composable
private fun Privacy(settings: Settings, onChanged: () -> Unit) {
    val context = LocalContext.current
    var level by remember { mutableStateOf(settings.securityLevel) }
    var lock by remember { mutableStateOf(settings.appLock) }
    var discreet by remember { mutableStateOf(settings.discreetIcon) }
    var preferOnion by remember { mutableStateOf(settings.preferOnion) }

    SectionHeader(stringResource(R.string.settings_security_level))
    listOf(
        SecurityLevel.STANDARD to (R.string.level_standard to R.string.level_standard_detail),
        SecurityLevel.SAFER to (R.string.level_safer to R.string.level_safer_detail),
        SecurityLevel.SAFEST to (R.string.level_safest to R.string.level_safest_detail),
    ).forEach { (value, labels) ->
        Choice(stringResource(labels.first), stringResource(labels.second), level == value) {
            level = value
            settings.securityLevel = value
            onChanged()
        }
    }

    SectionHeader(stringResource(R.string.settings_device))
    val lockAvailable = remember { AppLock.available(context) }
    Toggle(
        stringResource(R.string.setting_app_lock),
        stringResource(if (lockAvailable) R.string.setting_app_lock_detail else R.string.setting_app_lock_unavailable),
        lock, enabled = lockAvailable,
    ) { lock = it; settings.appLock = it }
    Toggle(stringResource(R.string.setting_discreet_icon), stringResource(R.string.setting_discreet_icon_detail), discreet) {
        discreet = it
        settings.discreetIcon = it
        LauncherIcon.apply(context, it)
    }
    Toggle(stringResource(R.string.setting_prefer_onion), stringResource(R.string.setting_prefer_onion_detail), preferOnion) {
        preferOnion = it
        settings.preferOnion = it
    }
    Hint(stringResource(R.string.setting_panic_tile_hint))

    var autoWipe by remember { mutableStateOf(settings.autoWipeMinutes) }
    SectionHeader(stringResource(R.string.setting_auto_wipe))
    listOf(-1 to R.string.auto_wipe_never, 0 to R.string.auto_wipe_leave, 5 to R.string.auto_wipe_5, 30 to R.string.auto_wipe_30)
        .forEach { (minutes, label) ->
            Choice(stringResource(label), null, autoWipe == minutes) {
                autoWipe = minutes
                settings.autoWipeMinutes = minutes
            }
        }
    Hint(stringResource(R.string.setting_auto_wipe_detail))
}

@Composable
private fun Connection(settings: Settings, onChanged: () -> Unit) {
    var mode by remember { mutableStateOf(settings.connectionMode) }
    var bridges by remember { mutableStateOf(settings.customBridges) }
    var exit by remember { mutableStateOf(settings.exitCountry) }
    var upstream by remember { mutableStateOf(settings.upstreamProxy) }
    var pickCountry by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_connect_via))
    listOf(
        ConnectionMode.AUTO to R.string.mode_auto_detail,
        ConnectionMode.DIRECT to R.string.mode_direct_detail,
        ConnectionMode.SNOWFLAKE to R.string.mode_snowflake_detail,
        ConnectionMode.OBFS4 to R.string.mode_obfs4_detail,
        ConnectionMode.MEEK to R.string.mode_meek_detail,
        ConnectionMode.CUSTOM to R.string.mode_custom_detail,
    ).forEach { (value, detail) ->
        Choice(modeLabel(value), stringResource(detail), mode == value) {
            mode = value
            settings.connectionMode = value
            if (value == ConnectionMode.AUTO) settings.autoStep = 0
            onChanged()
        }
    }

    if (mode == ConnectionMode.CUSTOM) {
        val parsed = Settings.parseBridgeLines(bridges)
        OutlinedTextField(
            value = bridges,
            onValueChange = { bridges = it; settings.customBridges = it; onChanged() },
            placeholder = { Text("obfs4 192.0.2.1:443 … cert=… iat-mode=0", style = MonoSmall) },
            textStyle = TextStyle(fontFamily = AppMono),
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
        )
        Hint(
            if (parsed.isEmpty()) stringResource(R.string.bridges_none)
            else stringResource(R.string.bridges_count, parsed.size, parsed.map { it.substringBefore(' ') }.toSet().joinToString()),
            color = if (parsed.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Hint(stringResource(R.string.bridges_where))
    }

    SectionHeader(stringResource(R.string.settings_routing))
    ListItem(
        headlineContent = { Text(stringResource(R.string.setting_exit_country)) },
        supportingContent = { Text(stringResource(R.string.setting_exit_country_detail)) },
        trailingContent = {
            Text(
                if (exit.isBlank()) stringResource(R.string.exit_auto) else "${Geo.flag(exit)} ${Geo.countryName(exit)}",
                color = MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { pickCountry = true }.padding(horizontal = 8.dp),
    )

    val upstreamValid = upstream.isBlank() || Settings.parseUpstream(upstream) != null
    OutlinedTextField(
        value = upstream,
        onValueChange = {
            upstream = it
            if (it.isBlank() || Settings.parseUpstream(it) != null) {
                settings.upstreamProxy = it.trim()
                onChanged()
            }
        },
        label = { Text(stringResource(R.string.setting_upstream)) },
        placeholder = { Text("socks5://host:1080") },
        isError = !upstreamValid,
        singleLine = true,
        textStyle = TextStyle(fontFamily = AppMono),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    )
    Hint(
        stringResource(if (upstreamValid) R.string.setting_upstream_detail else R.string.setting_upstream_invalid),
        color = if (upstreamValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )

    if (pickCountry) {
        AlertDialog(
            onDismissRequest = { pickCountry = false },
            title = { Text(stringResource(R.string.setting_exit_country)) },
            text = {
                LazyColumn {
                    items(EXIT_COUNTRIES) { code ->
                        Choice(
                            if (code.isBlank()) stringResource(R.string.exit_auto_recommended) else "${Geo.flag(code)}  ${Geo.countryName(code)}",
                            null,
                            exit == code,
                        ) {
                            exit = code
                            settings.exitCountry = code
                            pickCountry = false
                            onChanged()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCountry = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun Apps(settings: Settings, onKeepAlive: (Boolean) -> Unit) {
    val context = LocalContext.current
    var keepAlive by remember { mutableStateOf(settings.keepAlive) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        keepAlive = true
        onKeepAlive(true)
    }
    val address = "127.0.0.1:${TorController.SOCKS_PORT}"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.socks_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(address, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = AppMono))
            }
            IconButton(onClick = { copySensitive(context, address) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) }
        }
    }
    Toggle(stringResource(R.string.setting_keep_alive), stringResource(R.string.setting_keep_alive_detail), keepAlive) { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            keepAlive = enabled
            onKeepAlive(enabled)
        }
    }
    SectionHeader(stringResource(R.string.settings_wallets))
    Hint(stringResource(R.string.wallets_howto, address))
    Hint(stringResource(R.string.wallets_isolation))
    Hint(stringResource(R.string.wallets_no_proxy))
    SectionHeader(stringResource(R.string.settings_system_vpn))
    Hint(stringResource(R.string.system_vpn_detail))
}

@Composable
private fun Assistant(ask: AskViewModel, settings: Settings, onOpenLink: (String) -> Unit) {
    var zdr by remember { mutableStateOf(settings.aiZeroRetention) }
    var prompt by remember { mutableStateOf(settings.aiSystemPrompt) }
    var showModels by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { AiProviderForm(ask, onOpenLink) }

    SectionHeader(stringResource(R.string.settings_model))
    ListItem(
        headlineContent = { Text(ask.model) },
        supportingContent = { Text(stringResource(R.string.setting_model_detail)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = ask.ready) { showModels = true }.padding(horizontal = 8.dp),
    )
    Toggle(stringResource(R.string.setting_zdr), stringResource(R.string.setting_zdr_detail), zdr) {
        zdr = it
        settings.aiZeroRetention = it
    }
    SectionHeader(stringResource(R.string.setting_system_prompt))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Box(Modifier.heightIn(min = 96.dp).padding(16.dp)) {
            IncognitoField(
                initial = prompt,
                hint = stringResource(R.string.setting_system_prompt_hint),
                singleLine = false,
                imeAction = EditorInfo.IME_ACTION_NONE,
                onChange = { prompt = it; settings.aiSystemPrompt = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showModels) ModelSheet(ask, onDismiss = { showModels = false })
}

@Composable
private fun IdentitiesPage(browser: BrowserViewModel) {
    var editing by remember { mutableStateOf<app.antumbra.core.Identity?>(null) }
    var creating by remember { mutableStateOf(false) }
    Hint(stringResource(R.string.identities_detail))
    Spacer(Modifier.height(8.dp))
    browser.identities.forEach { identity ->
        ListItem(
            headlineContent = { Text(identity.name) },
            supportingContent = {
                val count = browser.tabs.count { it.identity.id == identity.id }
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.identity_tabs, count, count))
            },
            leadingContent = {
                Box(
                    Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(app.antumbra.core.Identity.color(identity.color))
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { editing = identity }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_rename)) }
                    IconButton(onClick = { browser.wipeIdentity(identity) }) { Icon(Icons.Outlined.CleaningServices, stringResource(R.string.identity_wipe)) }
                    if (browser.identities.size > 1) {
                        IconButton(onClick = { browser.deleteIdentity(identity) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.identity_add))
    }
    val target = editing
    if (creating || target != null) {
        var name by remember(target) { mutableStateOf(target?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(stringResource(if (target == null) R.string.identity_add else R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.identity_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (target == null) browser.addIdentity(name) else browser.renameIdentity(target, name)
                    creating = false
                    editing = null
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun About(onOpenLink: (String) -> Unit) {
    var licenses by remember { mutableStateOf(false) }
    val age = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - BuildConfig.BUILD_TIME)

    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.slogan), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionHeader(stringResource(R.string.settings_versions))
    KeyValueRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
    KeyValueRow(stringResource(R.string.about_engine), BuildConfig.GECKO_VERSION)
    KeyValueRow(stringResource(R.string.about_tor), BuildConfig.TOR_VERSION)
    if (age > 60) Hint(stringResource(R.string.about_engine_old, age), color = MaterialTheme.colorScheme.error)

    SectionHeader(stringResource(R.string.settings_threat_model))
    Hint(stringResource(R.string.threat_protects))
    Hint(stringResource(R.string.threat_limits))

    SectionHeader(stringResource(R.string.settings_legal))
    if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_privacy_policy)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onOpenLink(BuildConfig.PRIVACY_POLICY_URL) }.padding(horizontal = 8.dp),
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_licenses)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { licenses = true }.padding(horizontal = 8.dp),
    )
    Hint(stringResource(R.string.about_trademark))

    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = { Text(stringResource(R.string.licenses_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
