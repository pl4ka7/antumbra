package app.antumbra.ui.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.antumbra.R
import app.antumbra.ui.components.copySensitive
import app.antumbra.ui.theme.MonoSmall
import app.antumbra.web.BrowserViewModel
import app.antumbra.web.WebPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate

@Composable
fun PaymentDialog(uri: String, vm: BrowserViewModel) {
    val context = LocalContext.current
    val scheme = uri.substringBefore(':').lowercase()
    val walletConnect = scheme == "wc"
    val copied = stringResource(R.string.toast_copied_clears)
    val noWallet = stringResource(R.string.toast_no_wallet, scheme)
    AlertDialog(
        onDismissRequest = vm::dismissPayment,
        icon = { Icon(Icons.Outlined.AccountBalanceWallet, null) },
        title = { Text(stringResource(if (walletConnect) R.string.payment_title_wc else R.string.payment_title)) },
        text = {
            Column {
                Text(uri, style = MonoSmall, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(if (walletConnect) R.string.payment_warning_wc else R.string.payment_warning))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    vm.toast(noWallet)
                }
                vm.dismissPayment()
            }) { Text(stringResource(R.string.action_open_wallet)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { copySensitive(context, uri); vm.toast(copied); vm.dismissPayment() }) {
                    Text(stringResource(R.string.action_copy))
                }
                TextButton(onClick = vm::dismissPayment) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
fun WebPromptHost(vm: BrowserViewModel) {
    val prompt = vm.prompt ?: return
    val dismiss = { vm.answer(prompt.dismiss()) }
    val title: @Composable () -> Unit = { Text(stringResource(R.string.prompt_title)) }

    when (prompt) {
        is WebPrompt.Alert -> AlertDialog(
            onDismissRequest = dismiss,
            title = title,
            text = { Text(prompt.prompt.message.orEmpty()) },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_ok)) } },
        )

        is WebPrompt.Confirm -> AlertDialog(
            onDismissRequest = dismiss,
            title = title,
            text = { Text(prompt.prompt.message.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { vm.answer(prompt.prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.answer(prompt.prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is WebPrompt.Input -> {
            var value by remember(prompt) { mutableStateOf(prompt.prompt.defaultValue.orEmpty()) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = title,
                text = {
                    Column {
                        Text(prompt.prompt.message.orEmpty())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = { vm.answer(prompt.prompt.confirm(value)) }) { Text(stringResource(R.string.action_ok)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        is WebPrompt.Choice -> {
            val options = remember(prompt) {
                prompt.prompt.choices.flatMap { it.items?.toList() ?: listOf(it) }.filterNot { it.separator }
            }
            val multiple = prompt.prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
            val checked = remember(prompt) { mutableStateListOf(*options.filter { it.selected }.toTypedArray()) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(prompt.prompt.title ?: stringResource(R.string.prompt_choose)) },
                text = {
                    LazyColumn {
                        items(options) { option ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !option.disabled) {
                                        if (!multiple) vm.answer(prompt.prompt.confirm(option))
                                        else if (option in checked) checked.remove(option)
                                        else checked.add(option)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (multiple) Checkbox(checked = option in checked, onCheckedChange = null)
                                Text(
                                    option.label,
                                    color = when {
                                        option.disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        option.selected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (multiple) {
                        TextButton(onClick = { vm.answer(prompt.prompt.confirm(checked.toTypedArray())) }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}
