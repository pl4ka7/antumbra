package app.antumbra.ui.ask

import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.antumbra.R
import app.antumbra.ai.AskViewModel
import app.antumbra.ai.Message
import app.antumbra.ui.components.IncognitoField
import app.antumbra.ui.components.Mark
import app.antumbra.ui.components.copySensitive
import app.antumbra.ui.theme.MonoSmall
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    vm: AskViewModel,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showModels by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Message?>(null) }
    var clearSignal by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val copied = stringResource(R.string.toast_copied_clears)

    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.text?.length) {
        if (vm.messages.isNotEmpty()) list.animateScrollToItem(vm.messages.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
                title = {
                    if (vm.ready) {
                        Surface(
                            onClick = { showModels = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    vm.model,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp),
                                )
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Text(stringResource(R.string.ask_title))
                    }
                },
                actions = {
                    if (vm.messages.isNotEmpty()) {
                        IconButton(onClick = vm::clear) { Icon(Icons.Rounded.Add, stringResource(R.string.ask_new_chat)) }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, stringResource(R.string.ask_settings)) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!vm.ready) {
                Setup(vm, onOpenLink, Modifier.weight(1f))
                return@Column
            }
            Box(Modifier.weight(1f)) {
                if (vm.messages.isEmpty()) {
                    Empty(onPick = vm::send)
                } else {
                    LazyColumn(
                        state = list,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(vm.messages, key = { it.id }) { message ->
                            Bubble(
                                message = message,
                                isLast = message === vm.messages.lastOrNull(),
                                onLink = onOpenLink,
                                onCopy = { copySensitive(context, message.text); scope.launch { snackbar.showSnackbar(copied) } },
                                onRetry = vm::regenerate,
                                onReport = { reporting = message },
                            )
                        }
                    }
                }
            }
            Composer(
                busy = vm.busy,
                clearSignal = clearSignal,
                onChange = { draft = it },
                onSend = {
                    vm.send(draft)
                    draft = ""
                    clearSignal++
                },
                onStop = vm::stop,
                canSend = draft.isNotBlank(),
            )
        }
    }

    if (showModels) ModelSheet(vm, onDismiss = { showModels = false })
    reporting?.let { message ->
        val sent = stringResource(R.string.report_sent)
        val failed = stringResource(R.string.report_failed)
        ReportDialog(
            configured = vm.reportsConfigured,
            onDismiss = { reporting = null },
            onSend = { reason, note ->
                reporting = null
                vm.report(message, reason, note) { ok -> scope.launch { snackbar.showSnackbar(if (ok) sent else failed) } }
            },
        )
    }
}

@Composable
private fun Empty(onPick: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(R.string.ask_suggestion_1),
        stringResource(R.string.ask_suggestion_2),
        stringResource(R.string.ask_suggestion_3),
    )
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mark(Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.ask_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ask_empty_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        suggestions.forEach { text ->
            AssistChip(onClick = { onPick(text) }, label = { Text(text) }, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun Bubble(
    message: Message,
    isLast: Boolean,
    onLink: (String) -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onReport: () -> Unit,
) {
    if (message.fromUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        when {
            message.text.isEmpty() && message.streaming -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            message.text.isNotEmpty() -> Markdown(message.text, onLink)
        }
        message.error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.ask_error, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        AnimatedVisibility(!message.streaming) {
            Row {
                if (message.text.isNotEmpty()) {
                    IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), Modifier.size(18.dp)) }
                }
                if (isLast) {
                    IconButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, stringResource(R.string.ask_regenerate), Modifier.size(18.dp)) }
                }
                if (message.text.isNotEmpty()) {
                    IconButton(onClick = onReport) { Icon(Icons.Outlined.Flag, stringResource(R.string.report_action), Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    busy: Boolean,
    clearSignal: Int,
    canSend: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IncognitoField(
                initial = "",
                hint = stringResource(R.string.ask_hint),
                singleLine = false,
                imeAction = EditorInfo.IME_ACTION_NONE,
                onChange = onChange,
                clearSignal = clearSignal,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            if (busy) {
                FilledIconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, stringResource(R.string.ask_stop)) }
            } else {
                FilledIconButton(onClick = onSend, enabled = canSend) { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.ask_send)) }
            }
        }
    }
}

@Composable
private fun Setup(vm: AskViewModel, onOpenLink: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.ask_setup_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ask_setup_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        AiProviderForm(vm, onOpenLink)
        Spacer(Modifier.height(32.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.ask_no_account_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ask_no_account_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { onOpenLink(DUCK_AI) }) { Text(stringResource(R.string.ask_open_duck)) }
            }
        }
    }
}

const val DUCK_AI = "https://duck.ai/"
const val OPENROUTER_KEYS = "https://openrouter.ai/settings/keys"
