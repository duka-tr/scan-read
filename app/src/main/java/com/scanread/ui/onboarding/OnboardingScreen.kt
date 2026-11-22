package com.scanread.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanread.R
import com.scanread.core.permission.Permissions
import com.scanread.ui.MainViewModel
import com.scanread.ui.components.LanguageModels
import com.scanread.ui.components.LanguagePairPicker

private enum class Step { WELCOME, OVERLAY, NOTIFICATIONS, LANGUAGES }

/** First-run guide: what the app does, the permissions it needs, and the language files. */
@Composable
fun OnboardingRoute(viewModel: MainViewModel, onFinished: () -> Unit) {
    val context = LocalContext.current
    val steps = remember {
        Step.entries.filter { it != Step.NOTIFICATIONS || Permissions.isNotificationPermissionRequired }
    }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(Permissions.hasNotificationPermission(context)) }

    LifecycleResumeEffect(Unit) {
        overlayGranted = Permissions.canDrawOverlays(context)
        notificationsGranted = Permissions.hasNotificationPermission(context)
        viewModel.refreshModels()
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    val step = steps[stepIndex]
    val isLast = stepIndex == steps.lastIndex
    val next: () -> Unit = {
        if (isLast) onFinished() else stepIndex += 1
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    Step.WELCOME -> WelcomeStep()
                    Step.OVERLAY -> PermissionStep(
                        title = R.string.permission_overlay_title,
                        description = R.string.onboarding_overlay_text,
                        granted = overlayGranted,
                        onGrant = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
                    )
                    Step.NOTIFICATIONS -> PermissionStep(
                        title = R.string.permission_notifications_title,
                        description = R.string.onboarding_notifications_text,
                        granted = notificationsGranted,
                        onGrant = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                    Step.LANGUAGES -> LanguagesStep(viewModel)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The overlay permission is required; everything else can be done later.
                val canContinue = step != Step.OVERLAY || overlayGranted
                if (step == Step.NOTIFICATIONS && !notificationsGranted) {
                    TextButton(onClick = next) { Text(stringResource(R.string.onboarding_skip)) }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = next, enabled = canContinue) {
                    Text(stringResource(if (isLast) R.string.onboarding_finish else R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineSmall)
    listOf(
        R.string.onboarding_welcome_point_overlay,
        R.string.onboarding_welcome_point_private,
        R.string.onboarding_welcome_point_offline,
    ).forEach { point ->
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(point), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PermissionStep(@StringRes title: Int, @StringRes description: Int, granted: Boolean, onGrant: () -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.permission_status_granted), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.permission_action_grant)) }
    }
}

@Composable
private fun LanguagesStep(viewModel: MainViewModel) {
    val pair by viewModel.languagePair.collectAsStateWithLifecycle()
    val basic by viewModel.basicModel.collectAsStateWithLifecycle()
    val highQuality by viewModel.highQualityModel.collectAsStateWithLifecycle()

    Text(stringResource(R.string.onboarding_languages_title), style = MaterialTheme.typography.headlineSmall)
    LanguagePairPicker(
        pair = pair,
        sources = viewModel.sourceLanguages,
        targets = viewModel.targetLanguages,
        onChange = viewModel::setLanguages,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        LanguageModels(
            highQuality = highQuality,
            basic = basic,
            onDownloadHighQuality = viewModel::downloadHighQualityModel,
            onDeleteHighQuality = viewModel::deleteHighQualityModel,
            onDownloadBasic = viewModel::downloadBasicModel,
            onDeleteBasic = viewModel::deleteBasicModel,
            modifier = Modifier.padding(16.dp),
        )
    }
    Text(
        stringResource(R.string.onboarding_languages_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
