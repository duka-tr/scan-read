package tr.duka.scanread.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tr.duka.scanread.R
import tr.duka.scanread.ui.components.ScreenScaffold
import tr.duka.scanread.update.AppRelease
import tr.duka.scanread.ui.theme.ScanReadTheme

enum class ModelSummary { HIGH_QUALITY_READY, BASIC_READY, DOWNLOADING, NEEDED }

data class HomeUiState(
    val isServiceRunning: Boolean,
    val overlayGranted: Boolean,
    /** e.g. "English → Turkish". */
    val languages: String,
    val modelSummary: ModelSummary,
    /** A newer version available for download, if any. */
    val availableUpdate: AppRelease? = null,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleService: () -> Unit,
    onGrantOverlay: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            state.availableUpdate?.let { UpdateCard(it, onOpenUrl) }
            if (!state.overlayGranted) OverlayPermissionWarning(onGrantOverlay)

            Spacer(modifier = Modifier.height(8.dp))
            StartStopButton(
                isRunning = state.isServiceRunning,
                enabled = state.overlayGranted || state.isServiceRunning,
                onClick = onToggleService,
            )
            Text(
                text = stringResource(
                    if (state.isServiceRunning) R.string.service_status_running else R.string.home_status_stopped,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))

            LanguageCard(state.languages, state.modelSummary, onOpenLanguages)

            TextButton(onClick = onOpenSupport) {
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.home_support))
            }
        }
    }
}

@Composable
private fun StartStopButton(isRunning: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(136.dp),
        colors = if (isRunning) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            IconButtonDefaults.filledIconButtonColors()
        },
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Close else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (isRunning) R.string.service_action_stop else R.string.service_action_start),
            modifier = Modifier.size(64.dp),
        )
    }
}

@Composable
private fun UpdateCard(release: AppRelease, onOpenUrl: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.update_available_title, release.version.toString()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.update_available_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onOpenUrl(release.apkUrl ?: release.pageUrl) }) {
                    Text(stringResource(R.string.update_action_download))
                }
                TextButton(onClick = { onOpenUrl(release.pageUrl) }) {
                    Text(stringResource(R.string.update_action_notes))
                }
            }
        }
    }
}

@Composable
private fun OverlayPermissionWarning(onGrant: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.service_requires_overlay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.permission_action_grant)) }
        }
    }
}

@Composable
private fun LanguageCard(languages: String, summary: ModelSummary, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = languages, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                val status = when (summary) {
                    ModelSummary.HIGH_QUALITY_READY -> R.string.home_model_high_quality_ready
                    ModelSummary.BASIC_READY -> R.string.home_model_basic_ready
                    ModelSummary.DOWNLOADING -> R.string.translation_model_downloading
                    ModelSummary.NEEDED -> R.string.home_model_needed
                }
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary == ModelSummary.NEEDED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ScanReadTheme {
        HomeScreen(
            state = HomeUiState(
                isServiceRunning = false,
                overlayGranted = true,
                languages = "English → Turkish",
                modelSummary = ModelSummary.HIGH_QUALITY_READY,
            ),
            onToggleService = {},
            onGrantOverlay = {},
            onOpenUrl = {},
            onOpenLanguages = {},
            onOpenSettings = {},
            onOpenSupport = {},
        )
    }
}
