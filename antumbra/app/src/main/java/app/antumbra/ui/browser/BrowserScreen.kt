package app.antumbra.ui.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.antumbra.R
import app.antumbra.core.Identity
import app.antumbra.tor.TorState
import app.antumbra.ui.components.IncognitoField
import app.antumbra.ui.theme.LocalSignals
import app.antumbra.ui.theme.MonoSmall
import app.antumbra.web.BrowserViewModel
import app.antumbra.web.PendingDownload
import app.antumbra.web.Tab
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs

private enum class Sheet { Menu, Tabs, Shield }

@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    onAsk: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: (identities: Boolean) -> Unit,
    onPanic: () -> Unit,
    onRestart: () -> Unit,
) {
    val tab = vm.current ?: return
    val tor by vm.tor.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                GeckoHost(tab)
                if (tab.isHome && !tab.loading) {
                    HomeScreen(
                        vm = vm,
                        onSearch = { editing = true },
                        onAsk = onAsk,
                        onLibrary = onLibrary,
                        onIdentities = { sheet = Sheet.Tabs },
                    )
                }
                if (tor !is TorState.Ready) Offline(tor, onRestart)
            }
            Column(Modifier.navigationBarsPadding().imePadding()) {
                vm.downloadProgress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                AnimatedVisibility(
                    visible = tab.onionAlternative != null && !editing,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    AssistChip(
                        onClick = { vm.openOnionAlternative(tab) },
                        label = { Text(stringResource(R.string.onion_available)) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(LocalSignals.current.onion)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                val mode = when {
                    vm.find != null -> 2
                    editing -> 1
                    else -> 0
                }
                AnimatedContent(targetState = mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "toolbar") { target ->
                    when (target) {
                        2 -> FindBar(vm)
                        1 -> AddressEditor(
                            initial = if (tab.isHome) "" else tab.url,
                            onSubmit = { vm.load(it); editing = false },
                            onCancel = { editing = false },
                        )
                        else -> Toolbar(
                            vm = vm,
                            tab = tab,
                            onAddress = { editing = true },
                            onShield = { sheet = Sheet.Shield },
                            onTabs = { sheet = Sheet.Tabs },
                            onMenu = { sheet = Sheet.Menu },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    when (sheet) {
        Sheet.Menu -> MenuSheet(
            vm = vm, tab = tab, onDismiss = { sheet = null },
            onAsk = onAsk, onLibrary = onLibrary, onSettings = { onSettings(false) }, onPanic = onPanic,
        )
        Sheet.Tabs -> TabsSheet(vm, onDismiss = { sheet = null }, onManageIdentities = { sheet = null; onSettings(true) })
        Sheet.Shield -> ShieldSheet(vm, tab, onDismiss = { sheet = null })
        null -> Unit
    }
    vm.download?.let { DownloadDialog(it, vm) }
    vm.paymentUri?.let { PaymentDialog(it, vm) }
    WebPromptHost(vm)
}

@Composable
private fun GeckoHost(tab: Tab) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { GeckoView(it) },
        update = { view ->
            if (view.session !== tab.session) {
                view.releaseSession()
                view.setSession(tab.session)
            }
        },
        onRelease = { it.releaseSession() },
    )
}

@Composable
private fun Toolbar(
    vm: BrowserViewModel,
    tab: Tab,
    onAddress: () -> Unit,
    onShield: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val signals = LocalSignals.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }, enabled = tab.canBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerInput(vm.tabs.size) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                if (abs(dragged) > 120f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.selectRelative(if (dragged < 0) 1 else -1)
                                }
                            },
                        ) { _, delta -> dragged += delta }
                    },
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .padding(start = 6.dp, end = 4.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !tab.isHome, onClick = onShield)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = when {
                            tab.isHome -> MaterialTheme.colorScheme.onSurfaceVariant
                            tab.isOnion -> signals.onion
                            tab.secure -> signals.safe
                            else -> MaterialTheme.colorScheme.error
                        }
                        Icon(
                            if (tab.isHome) Icons.Rounded.Search else Icons.Rounded.Shield,
                            stringResource(R.string.cd_shield), Modifier.size(18.dp), tint = tint,
                        )
                        if (!tab.isHome && tab.blocked.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (tab.blocked.size > 99) "99+" else tab.blocked.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = tint,
                            )
                        }
                    }
                    Text(
                        if (tab.isHome) stringResource(R.string.address_hint) else tab.host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tab.isHome) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable(onClick = onAddress).padding(start = 4.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
                if (tab.loading) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                        color = if (tab.isOnion) signals.onion else MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        drawStopIndicator = {},
                    )
                }
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTabs()
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(24.dp).border(2.dp, Identity.color(tab.identity.color), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (vm.tabs.size > 99) "99+" else "${vm.tabs.size}", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_menu)) }
        }
    }
}

@Composable
private fun AddressEditor(initial: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.height(60.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IncognitoField(
                initial = initial,
                hint = stringResource(R.string.address_hint),
                modifier = Modifier.weight(1f),
                autoFocus = true,
                imeAction = EditorInfo.IME_ACTION_GO,
                onSubmit = onSubmit,
            )
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun FindBar(vm: BrowserViewModel) {
    val state = vm.find ?: return
    var query by remember { mutableStateOf(state.query) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.height(60.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IncognitoField(
                initial = "",
                hint = stringResource(R.string.find_hint),
                modifier = Modifier.weight(1f),
                autoFocus = true,
                imeAction = EditorInfo.IME_ACTION_SEARCH,
                onChange = { query = it; vm.find(it) },
                onSubmit = { vm.find(it) },
            )
            if (state.query.isNotEmpty()) {
                Text(
                    if (state.total == 0) "0/0" else "${state.current}/${state.total}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.find(query, backwards = true) }, enabled = state.total > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.find_previous))
            }
            IconButton(onClick = { vm.find(query) }, enabled = state.total > 0) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.find_next))
            }
            IconButton(onClick = vm::clearFind) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
        }
    }
}

/** ACTION_CREATE_DOCUMENT with a MIME type and suggested file name chosen at launch time. */
private class CreateFile : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>) =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
private fun DownloadDialog(download: PendingDownload, vm: BrowserViewModel) {
    val launcher = rememberLauncherForActivityResult(CreateFile()) { uri ->
        if (uri != null) vm.saveDownload(uri) else vm.cancelDownload()
    }
    AlertDialog(
        onDismissRequest = vm::cancelDownload,
        icon = { Icon(Icons.Outlined.Download, null) },
        title = { Text(stringResource(R.string.download_title)) },
        text = {
            Column {
                Text(download.fileName, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(download.mimeType, download.size.takeIf { it > 0 }?.let(::formatSize)).joinToString(" · "),
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.download_warning))
            }
        },
        confirmButton = {
            TextButton(onClick = { launcher.launch(download.fileName to download.mimeType) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = vm::cancelDownload) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}

@Composable
private fun Offline(state: TorState, onRestart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)).clickable(enabled = true) {}.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.SignalWifiOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.offline_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.offline_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (state is TorState.Failed) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onRestart) { Text(stringResource(R.string.action_restart)) }
        }
    }
}
