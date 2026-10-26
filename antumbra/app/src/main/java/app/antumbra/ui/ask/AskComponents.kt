package app.antumbra.ui.ask

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.antumbra.R
import app.antumbra.ai.AskViewModel
import app.antumbra.ai.Model
import app.antumbra.ai.ModelsState
import app.antumbra.ai.ReportReason
import app.antumbra.core.AiProvider
import app.antumbra.ui.components.IncognitoField
import app.antumbra.ui.theme.LocalSignals
import app.antumbra.ui.theme.MonoSmall
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderForm(vm: AskViewModel, onOpenLink: (String) -> Unit) {
    var provider by remember { mutableStateOf(vm.provider) }
    var key by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(vm.baseUrl) }
    var rememberKey by remember { mutableStateOf(true) }
    val providers = listOf(AiProvider.OPENROUTER to R.string.ai_provider_openrouter, AiProvider.CUSTOM to R.string.ai_provider_custom)

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        providers.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = provider == value,
                onClick = { provider = value },
                shape = SegmentedButtonDefaults.itemShape(index, providers.size),
            ) { Text(stringResource(label)) }
        }
    }
    Spacer(Modifier.height(20.dp))

    if (provider == AiProvider.CUSTOM) {
        FieldBox(stringResource(R.string.ai_base_url)) {
            IncognitoField(
                initial = baseUrl,
                hint = "http://example.onion/v1",
                imeAction = EditorInfo.IME_ACTION_NEXT,
                onChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (vm.hasKey && provider == vm.provider) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = LocalSignals.current.safe)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ai_key_saved), modifier = Modifier.weight(1f))
                TextButton(onClick = vm::forgetKey) { Text(stringResource(R.string.ai_key_remove)) }
            }
        }
    } else {
        FieldBox(stringResource(if (provider == AiProvider.CUSTOM) R.string.ai_key_optional else R.string.ai_key)) {
            IncognitoField(
                initial = "",
                hint = if (provider == AiProvider.OPENROUTER) "sk-or-…" else "",
                password = true,
                imeAction = EditorInfo.IME_ACTION_DONE,
                onChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { rememberKey = !rememberKey },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_remember_key), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.ai_remember_key_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = rememberKey, onCheckedChange = { rememberKey = it })
        }
        if (provider == AiProvider.OPENROUTER) {
            TextButton(onClick = { onOpenLink(OPENROUTER_KEYS) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.ai_get_key))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.ai_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    val canSave = when (provider) {
        AiProvider.OPENROUTER -> key.isNotBlank() || (vm.hasKey && vm.provider == AiProvider.OPENROUTER)
        AiProvider.CUSTOM -> baseUrl.startsWith("http")
    }
    Button(
        onClick = {
            vm.configure(provider, baseUrl)
            if (key.isNotBlank()) vm.setKey(key, rememberKey)
        },
        enabled = canSave,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(stringResource(R.string.action_save)) }
}

@Composable
private fun FieldBox(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(vm: AskViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadModels() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.models_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.models_search)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = freeOnly, onClick = { freeOnly = !freeOnly }, label = { Text(stringResource(R.string.models_free)) })
            }
            var manual by remember { mutableStateOf(vm.model) }
            val manualId = manual.trim()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.models_manual), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manual,
                            onValueChange = { manual = it.replace(" ", "") },
                            singleLine = true,
                            placeholder = { Text("provider/model-name", style = MonoSmall) },
                            textStyle = MonoSmall,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { vm.selectModel(manualId); onDismiss() },
                            enabled = manualId.isNotEmpty(),
                        ) { Text(stringResource(R.string.models_use)) }
                    }
                    Text(
                        stringResource(R.string.models_manual_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when (val state = vm.models) {
            ModelsState.Idle, ModelsState.Loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ModelsState.Failed -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.models_error, state.message), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.loadModels(force = true) }) { Text(stringResource(R.string.action_retry)) }
            }
            is ModelsState.Loaded -> {
                val filtered = state.models.filter { model ->
                    (!freeOnly || model.free) &&
                        (query.isBlank() || model.name.contains(query, true) || model.id.contains(query, true))
                }
                LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(filtered, key = { it.id }) { model ->
                        ModelRow(model, selected = model.id == vm.model) {
                            vm.selectModel(model.id)
                            onDismiss()
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModelRow(model: Model, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.id, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (model.name != model.id) {
                Text(model.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val details = buildList {
                if (model.contextLength > 0) add(stringResource(R.string.models_context, compact(model.contextLength)))
                when {
                    model.free -> add(stringResource(R.string.models_free))
                    model.inputPrice != null && model.outputPrice != null ->
                        add(stringResource(R.string.models_price, price(model.inputPrice), price(model.outputPrice)))
                }
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

private fun compact(tokens: Int) = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}

private fun price(value: Double) = String.format(Locale.ROOT, if (value < 1) "%.2f" else "%.1f", value)

@Composable
fun ReportDialog(configured: Boolean, onDismiss: () -> Unit, onSend: (ReportReason, String) -> Unit) {
    var reason by remember { mutableStateOf(ReportReason.OFFENSIVE) }
    var note by remember { mutableStateOf("") }
    val labels = mapOf(
        ReportReason.OFFENSIVE to R.string.report_offensive,
        ReportReason.SEXUAL to R.string.report_sexual,
        ReportReason.VIOLENT to R.string.report_violent,
        ReportReason.DANGEROUS to R.string.report_dangerous,
        ReportReason.MISLEADING to R.string.report_misleading,
        ReportReason.OTHER to R.string.report_other,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column {
                Text(stringResource(R.string.report_detail), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                labels.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == value, role = Role.RadioButton) { reason = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1_000) },
                    placeholder = { Text(stringResource(R.string.report_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!configured) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.report_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, note) }, enabled = configured) { Text(stringResource(R.string.report_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
