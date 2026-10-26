package app.antumbra.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.antumbra.R
import app.antumbra.core.OnionDirectory
import app.antumbra.ui.browser.SiteAvatar
import app.antumbra.ui.components.SectionHeader
import app.antumbra.ui.theme.LocalSignals
import app.antumbra.ui.theme.MonoSmall
import app.antumbra.web.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: BrowserViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(if (vm.vault.isEmpty()) 1 else 0) }
    val directory = remember { OnionDirectory.load(context).groupBy { it.category } }
    val signals = LocalSignals.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                PrimaryTabRow(selectedTabIndex = page, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = page == 0, onClick = { page = 0 }, text = { Text(stringResource(R.string.library_vault)) })
                    Tab(selected = page == 1, onClick = { page = 1 }, text = { Text(stringResource(R.string.library_directory)) })
                }
            }
        },
    ) { padding ->
        if (page == 0) {
            if (vm.vault.isEmpty()) {
                Column(
                    Modifier.padding(padding).fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.vault_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.vault_empty_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.vault_encrypted), style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = signals.safe) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    items(vm.vault, key = { it.url }) { bookmark ->
                        ListItem(
                            headlineContent = { Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(bookmark.url, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = { SiteAvatar(bookmark.title, bookmark.url, size = 40) },
                            trailingContent = {
                                IconButton(onClick = { vm.removeSaved(bookmark) }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpen(bookmark.url) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        stringResource(R.string.directory_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                directory.forEach { (category, sites) ->
                    item(key = "h-$category") { SectionHeader(stringResource(categoryLabel(category))) }
                    items(sites, key = { it.url }) { site ->
                        ListItem(
                            headlineContent = { Text(site.name) },
                            supportingContent = {
                                Text(
                                    site.url.substringAfter("://").removeSuffix("/"),
                                    style = MonoSmall,
                                    color = signals.onion,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { SiteAvatar(site.name, site.url, size = 40) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpen(site.url) },
                        )
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(stringResource(R.string.directory_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun categoryLabel(category: String) = when (category) {
    "news" -> R.string.directory_news
    "search" -> R.string.directory_search
    "privacy" -> R.string.directory_privacy
    "tech" -> R.string.directory_tech
    "social" -> R.string.directory_social
    else -> R.string.directory_government
}
