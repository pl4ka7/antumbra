package app.antumbra.ui.boot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.antumbra.R
import app.antumbra.core.ConnectionMode
import app.antumbra.tor.TorState
import app.antumbra.ui.components.Mark
import app.antumbra.ui.theme.MonoSmall
import kotlinx.coroutines.delay

private const val STALL_MS = 40_000L

@Composable
fun WelcomeScreen(onConnect: () -> Unit, onConfigure: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Mark(Modifier.size(72.dp))
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.slogan),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        Point(Icons.Outlined.Layers, stringResource(R.string.welcome_point_tor))
        Point(Icons.Outlined.VisibilityOff, stringResource(R.string.welcome_point_trace))
        Point(Icons.Outlined.Dns, stringResource(R.string.welcome_point_bridges))
        Spacer(Modifier.weight(1.4f))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.action_connect))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_configure_connection))
        }
    }
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ConnectingScreen(
    state: TorState,
    mode: ConnectionMode,
    autoMode: Boolean,
    onStalled: () -> Unit,
    onRetry: () -> Unit,
    onConfigure: () -> Unit,
) {
    val progress = when (state) {
        is TorState.Starting -> state.progress / 100f
        TorState.Ready -> 1f
        else -> 0f
    }
    val animated by animateFloatAsState(progress, spring(stiffness = 60f), label = "progress")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val stalled = state is TorState.Starting && state.progress < 100 && now - state.since > STALL_MS
    LaunchedEffect(stalled) { if (stalled && autoMode) onStalled() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Mark(Modifier.size(168.dp), progress = animated)
            Spacer(Modifier.height(32.dp))
            AnimatedContent(
                targetState = state is TorState.Failed,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status",
            ) { failed ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (failed) R.string.boot_failed else R.string.boot_connecting),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (state) {
                            is TorState.Starting -> "${state.progress}% · ${modeLabel(mode)}"
                            is TorState.Failed -> state.reason
                            else -> modeLabel(mode)
                        },
                        style = MonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (state is TorState.Starting && state.summary.isNotBlank()) {
                        Text(
                            state.summary, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if ((stalled && !autoMode) || state is TorState.Failed) {
                Text(
                    stringResource(R.string.boot_blocked_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Button(onClick = onConfigure, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.action_configure_connection))
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
fun modeLabel(mode: ConnectionMode): String = stringResource(
    when (mode) {
        ConnectionMode.AUTO -> R.string.mode_auto
        ConnectionMode.DIRECT -> R.string.mode_direct
        ConnectionMode.SNOWFLAKE -> R.string.mode_snowflake
        ConnectionMode.OBFS4 -> R.string.mode_obfs4
        ConnectionMode.MEEK -> R.string.mode_meek
        ConnectionMode.CUSTOM -> R.string.mode_custom
    }
)
