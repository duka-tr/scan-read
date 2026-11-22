package com.scanread.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanread.R
import com.scanread.ui.theme.ScanReadTheme

data class HomeUiState(
    val overlayGranted: Boolean,
    val showNotificationPermission: Boolean,
    val notificationsGranted: Boolean,
    val isServiceRunning: Boolean,
    val sourceLanguageName: String,
    val targetLanguageName: String,
    val modelState: ModelState,
    val highQualityInstalled: Boolean,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onGrantOverlay: () -> Unit,
    onGrantNotifications: () -> Unit,
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(stringResource(R.string.home_section_permissions))
            PermissionCard(
                title = stringResource(R.string.permission_overlay_title),
                description = stringResource(R.string.permission_overlay_description),
                granted = state.overlayGranted,
                onGrant = onGrantOverlay,
            )
            if (state.showNotificationPermission) {
                PermissionCard(
                    title = stringResource(R.string.permission_notifications_title),
                    description = stringResource(R.string.permission_notifications_description),
                    granted = state.notificationsGranted,
                    onGrant = onGrantNotifications,
                )
            }

            SectionHeader(stringResource(R.string.home_section_translation))
            TranslationModelCard(
                languages = stringResource(
                    R.string.translation_languages,
                    state.sourceLanguageName,
                    state.targetLanguageName,
                ),
                modelState = state.modelState,
                highQualityInstalled = state.highQualityInstalled,
                onDownload = onDownloadModel,
                onDelete = onDeleteModel,
            )

            SectionHeader(stringResource(R.string.home_section_service))
            ServiceCard(
                isRunning = state.isServiceRunning,
                canStart = state.overlayGranted,
                onStart = onStartService,
                onStop = onStopService,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (granted) {
                Text(
                    text = stringResource(R.string.permission_status_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                FilledTonalButton(onClick = onGrant) {
                    Text(stringResource(R.string.permission_action_grant))
                }
            }
        }
    }
}

@Composable
private fun TranslationModelCard(
    languages: String,
    modelState: ModelState,
    highQualityInstalled: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = languages, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            if (highQualityInstalled) {
                Text(
                    text = stringResource(R.string.translation_engine_high_quality),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val status = when (modelState) {
                ModelState.CHECKING -> R.string.translation_model_checking
                ModelState.MISSING -> R.string.translation_model_missing
                ModelState.DOWNLOADING -> R.string.translation_model_downloading
                ModelState.READY -> R.string.translation_model_ready
                ModelState.ERROR -> R.string.translation_model_error
            }
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (modelState) {
                ModelState.MISSING, ModelState.ERROR -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = onDownload) {
                        Text(stringResource(R.string.translation_action_download))
                    }
                }
                ModelState.DOWNLOADING -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                ModelState.READY -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.translation_action_delete))
                    }
                }
                ModelState.CHECKING -> Unit
            }
        }
    }
}

@Composable
private fun ServiceCard(
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val status = when {
                isRunning -> R.string.service_status_running
                canStart -> R.string.service_status_stopped
                else -> R.string.service_requires_overlay
            }
            Text(text = stringResource(status), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            if (isRunning) {
                OutlinedButton(onClick = onStop) {
                    Text(stringResource(R.string.service_action_stop))
                }
            } else {
                Button(onClick = onStart, enabled = canStart) {
                    Text(stringResource(R.string.service_action_start))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ScanReadTheme {
        HomeScreen(
            state = HomeUiState(
                overlayGranted = true,
                showNotificationPermission = true,
                notificationsGranted = false,
                isServiceRunning = false,
                sourceLanguageName = "English",
                targetLanguageName = "Turkish",
                modelState = ModelState.MISSING,
                highQualityInstalled = false,
            ),
            onGrantOverlay = {},
            onGrantNotifications = {},
            onDownloadModel = {},
            onDeleteModel = {},
            onStartService = {},
            onStopService = {},
        )
    }
}
