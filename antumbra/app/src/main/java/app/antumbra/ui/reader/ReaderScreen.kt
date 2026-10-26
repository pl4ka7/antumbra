package app.antumbra.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.antumbra.R
import app.antumbra.ui.theme.MonoMedium
import app.antumbra.web.Article

/** Distraction-free article view. Text only: no scripts, images or third-party requests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(article: Article, canSummarize: Boolean, onSummarize: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    val words = article.plainText.split(Regex("\\s+")).size
    val minutes = (words / 220).coerceAtLeast(1)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(article.site, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.8f) }) { Icon(Icons.Rounded.TextDecrease, stringResource(R.string.reader_smaller)) }
                    IconButton(onClick = { scale = (scale + 0.1f).coerceAtMost(1.6f) }) { Icon(Icons.Rounded.TextIncrease, stringResource(R.string.reader_larger)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (canSummarize) {
                ExtendedFloatingActionButton(
                    onClick = onSummarize,
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) },
                    text = { Text(stringResource(R.string.reader_summarize)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(article.byline.ifBlank { null }, stringResource(R.string.reader_minutes, minutes)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
            items(article.blocks) { block ->
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(bottom = 14.dp)) {
                    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * scale).sp, lineHeight = (28 * scale).sp)
                    when (block.tag) {
                        "h1", "h2" -> Text(block.text, style = body.copy(fontSize = (21 * scale).sp, fontWeight = FontWeight.SemiBold))
                        "h3" -> Text(block.text, style = body.copy(fontWeight = FontWeight.SemiBold))
                        "li" -> Row { Text("•  ", style = body); Text(block.text, style = body) }
                        "blockquote" -> Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(block.text, style = body.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(14.dp))
                        }
                        "pre" -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)) {
                            Text(block.text, style = MonoMedium, modifier = Modifier.padding(12.dp))
                        }
                        else -> Text(block.text, style = body)
                    }
                }
            }
        }
    }
}
