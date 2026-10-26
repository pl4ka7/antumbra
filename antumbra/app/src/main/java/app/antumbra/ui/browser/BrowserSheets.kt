package app.antumbra.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.antumbra.R
import app.antumbra.core.Identity
import app.antumbra.tor.Geo
import app.antumbra.ui.boot.modeLabel
import app.antumbra.ui.theme.LocalSignals
import app.antumbra.ui.theme.MonoSmall
import app.antumbra.web.BrowserViewModel
import app.antumbra.web.CHECK_URL
import app.antumbra.web.CircuitView
import app.antumbra.web.Tab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    vm: BrowserViewModel,
    tab: Tab,
    onDismiss: () -> Unit,
    onAsk: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onPanic: () -> Unit,
) {
    var confirmPanic by remember { mutableStateOf(false) }
    val onPage = !tab.isHome
    val act: (() -> Unit) -> () -> Unit = { action -> { onDismiss(); action() } }
    val saved = onPage && vm.isSaved(tab.url)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Status(vm)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile(Icons.Outlined.Add, stringResource(R.string.menu_new_tab), Modifier.weight(1f), act { vm.newTab() })
                Tile(Icons.Outlined.Fingerprint, stringResource(R.string.menu_new_identity), Modifier.weight(1f), act { vm.newIdentity() })
                Tile(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.menu_ask), Modifier.weight(1f), act(onAsk))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.menu_reader), Modifier.weight(1f), act { vm.openReader() }, onPage)
                Tile(
                    if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    stringResource(if (saved) R.string.menu_saved else R.string.menu_save),
                    Modifier.weight(1f), act { vm.toggleSaved(tab) }, onPage,
                )
                Tile(Icons.Outlined.Explore, stringResource(R.string.menu_library), Modifier.weight(1f), act(onLibrary))
            }
            Item(Icons.Outlined.Shuffle, stringResource(R.string.menu_new_circuit), stringResource(R.string.menu_new_circuit_detail), onPage, act { vm.newCircuitForSite() })
            Item(Icons.Outlined.FindInPage, stringResource(R.string.menu_find), null, onPage, act { vm.startFind() })
            Item(Icons.Outlined.Refresh, stringResource(R.string.menu_reload), null, true, act { vm.reload() })
            Item(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.menu_forward), null, tab.canForward, act { vm.forward() })
            Item(Icons.Outlined.Home, stringResource(R.string.menu_home), null, onPage, act { vm.goHome() })
            Item(Icons.Outlined.VerifiedUser, stringResource(R.string.menu_verify), null, true, act { vm.load(CHECK_URL) })
            Item(Icons.Outlined.Settings, stringResource(R.string.menu_settings), null, true, act(onSettings))
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Item(
                Icons.Outlined.DeleteForever, stringResource(R.string.menu_panic), stringResource(R.string.menu_panic_detail), true,
                { confirmPanic = true }, tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmPanic) {
        AlertDialog(
            onDismissRequest = { confirmPanic = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text(stringResource(R.string.panic_title)) },
            text = { Text(stringResource(R.string.panic_text)) },
            confirmButton = { TextButton(onClick = onPanic) { Text(stringResource(R.string.action_wipe), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmPanic = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Status(vm: BrowserViewModel) {
    val signals = LocalSignals.current
    val parts = buildList {
        add(modeLabel(vm.tor.mode))
        if (vm.tor.exitCountry.isNotBlank()) add(stringResource(R.string.status_exit, Geo.flag(vm.tor.exitCountry)))
        if (vm.settings.keepAlive) add(stringResource(R.string.status_proxy_on))
    }
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(signals.safe))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.status_connected), style = MaterialTheme.typography.titleSmall)
            Text(parts.joinToString(" · "), style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    val content = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 84.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = content)
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 2)
        }
    }
}

@Composable
private fun Item(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = color) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(vm: BrowserViewModel, onDismiss: () -> Unit, onManageIdentities: () -> Unit) {
    val signals = LocalSignals.current
    var filter by remember { mutableStateOf(vm.activeIdentity?.id) }
    val visible = vm.tabs.filter { filter == null || it.identity.id == filter }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tabs_title, vm.tabs.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.closeAll(); onDismiss() }) { Text(stringResource(R.string.tabs_close_all)) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.identity_all)) })
            vm.identities.forEach { identity ->
                val color = Identity.color(identity.color)
                FilterChip(
                    selected = filter == identity.id,
                    onClick = { filter = identity.id; vm.useIdentity(identity) },
                    label = { Text(identity.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.22f)),
                )
            }
            IconButton(onClick = onManageIdentities) { Icon(Icons.Outlined.Tune, stringResource(R.string.identity_manage)) }
        }
        Text(
            stringResource(R.string.identity_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.heightIn(max = 520.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = { it.id }) { item ->
                val active = item.id == vm.currentId
                val color = Identity.color(item.identity.color)
                Surface(
                    onClick = { vm.select(item); onDismiss() },
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.aspectRatio(1.2f),
                ) {
                    Column(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            if (item.isOnion) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(8.dp).clip(CircleShape).background(signals.onion))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.close(item) }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                        }
                        Spacer(Modifier.weight(1f))
                        if (!item.isHome) SiteAvatar(item.title, item.url, size = 32)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.title.ifBlank { if (item.isHome) stringResource(R.string.tab_new) else item.host },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                        Text(
                            if (item.isHome) item.identity.name else item.host,
                            style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val identity = vm.identities.firstOrNull { it.id == filter } ?: vm.activeIdentity
                        vm.newTab(identity = identity)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.aspectRatio(1.2f),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.menu_new_tab))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Per-site panel: connection, circuit, blocked trackers and site controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSheet(vm: BrowserViewModel, tab: Tab, onDismiss: () -> Unit) {
    val signals = LocalSignals.current
    val isolated by vm.isolationActive.collectAsState()
    LaunchedEffect(tab.id, tab.url) { vm.loadCircuit() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteAvatar(tab.title, tab.url, size = 40)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tab.host, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val (label, color) = when {
                        tab.isOnion -> stringResource(R.string.shield_onion) to signals.onion
                        tab.secure -> stringResource(R.string.shield_secure) to signals.safe
                        else -> stringResource(R.string.shield_insecure) to MaterialTheme.colorScheme.error
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge(
                    tab.blocked.size.toString(),
                    pluralStringResource(R.plurals.shield_trackers, tab.blocked.size),
                    Modifier.weight(1f),
                )
                Badge(
                    tab.identity.name,
                    stringResource(R.string.shield_identity),
                    Modifier.weight(1f),
                    dot = Identity.color(tab.identity.color),
                )
            }
            if (tab.blocked.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tab.blocked.take(8).joinToString("\n") + if (tab.blocked.size > 8) "\n…" else "",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.circuit_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            vm.circuit?.let { CircuitPath(it) }
            Text(
                stringResource(if (isolated) R.string.circuit_isolation_on else R.string.circuit_isolation_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            Toggle(stringResource(R.string.shield_javascript), stringResource(R.string.shield_javascript_detail), tab.javascript) {
                vm.setJavascript(tab, it)
            }
            Toggle(stringResource(R.string.shield_desktop), null, tab.desktop) { vm.setDesktop(tab, it) }

            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { vm.newCircuitForSite(); onDismiss() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.Shuffle, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.menu_new_circuit))
            }
        }
    }
}

@Composable
private fun Badge(value: String, label: String, modifier: Modifier, dot: Color? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CircuitPath(view: CircuitView) {
    val circuit = view.circuit
    when {
        view.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.circuit_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        circuit == null -> Text(stringResource(R.string.circuit_none), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        else -> {
            val onion = view.host.endsWith(".onion")
            Node(stringResource(R.string.circuit_you), null, first = true)
            circuit.relays.forEachIndexed { index, relay ->
                val role = stringResource(
                    when {
                        relay.bridge -> R.string.circuit_bridge
                        index == 0 -> R.string.circuit_guard
                        index == circuit.relays.lastIndex -> if (onion) R.string.circuit_rendezvous else R.string.circuit_exit
                        else -> R.string.circuit_middle
                    }
                )
                val place = relay.country?.let { "${Geo.flag(it)}  ${Geo.countryName(it)}" } ?: stringResource(R.string.circuit_unknown_country)
                Node("$role · $place", listOfNotNull(relay.nickname, relay.address.takeUnless { relay.bridge }).joinToString(" · "))
            }
            Node(if (onion) stringResource(R.string.circuit_onion_service) else view.host, null, last = true)
        }
    }
}

@Composable
private fun Node(title: String, detail: String?, first: Boolean = false, last: Boolean = false) {
    val line = MaterialTheme.colorScheme.outline
    Row {
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(10.dp).background(if (first) Color.Transparent else line))
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(if (first || last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            )
            Box(Modifier.width(2.dp).height(if (detail != null) 30.dp else 18.dp).background(if (last) Color.Transparent else line))
        }
        Column(Modifier.padding(start = 12.dp, top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
