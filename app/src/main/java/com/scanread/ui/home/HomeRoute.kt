package com.scanread.ui.home

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanread.R
import com.scanread.core.permission.Permissions
import com.scanread.service.ScreenTranslateService
import java.util.Locale

/** Wires the home screen to permissions, system dialogs, the translation service and model state. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val context = LocalContext.current

    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(Permissions.hasNotificationPermission(context)) }
    val isServiceRunning by ScreenTranslateService.isRunning.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val highQuality by viewModel.highQuality.collectAsStateWithLifecycle()

    // Permissions and installed models may change while the app is in the background.
    LifecycleResumeEffect(Unit) {
        overlayGranted = Permissions.canDrawOverlays(context)
        notificationsGranted = Permissions.hasNotificationPermission(context)
        viewModel.refreshModelState()
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    val captureConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenTranslateService.start(context, result.resultCode, data)
        } else {
            Toast.makeText(context, R.string.error_capture_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    HomeScreen(
        state = HomeUiState(
            overlayGranted = overlayGranted,
            showNotificationPermission = Permissions.isNotificationPermissionRequired,
            notificationsGranted = notificationsGranted,
            isServiceRunning = isServiceRunning,
            sourceLanguageName = languageDisplayName(viewModel.languagePair.source),
            targetLanguageName = languageDisplayName(viewModel.languagePair.target),
            modelState = modelState,
            highQuality = highQuality,
            highQualitySizeMb = viewModel.highQualitySizeMb,
        ),
        onGrantOverlay = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        onGrantNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onDownloadModel = viewModel::downloadModel,
        onDeleteModel = viewModel::deleteModel,
        onDownloadHighQuality = viewModel::downloadHighQualityModel,
        onDeleteHighQuality = viewModel::deleteHighQualityModel,
        onStartService = {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            captureConsentLauncher.launch(manager.createScreenCaptureIntent())
        },
        onStopService = { ScreenTranslateService.stop(context) },
    )
}

/** Language name in the user's language, e.g. "en" → "English" / "İngilizce". */
private fun languageDisplayName(code: String): String {
    val displayLocale = Locale.getDefault()
    return Locale.forLanguageTag(code)
        .getDisplayLanguage(displayLocale)
        .replaceFirstChar { it.titlecase(displayLocale) }
}
