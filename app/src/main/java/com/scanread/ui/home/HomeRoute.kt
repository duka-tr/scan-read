package com.scanread.ui.home

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
import com.scanread.R
import com.scanread.core.locale.LanguageNames
import com.scanread.core.permission.Permissions
import com.scanread.service.ScreenTranslateService
import com.scanread.translate.opusmt.HighQualityModels
import com.scanread.ui.MainViewModel
import com.scanread.ui.ModelState

/** Wires the home screen to permissions, the capture consent dialog and the translation service. */
@Composable
fun HomeRoute(
    viewModel: MainViewModel,
    onOpenLanguages: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    val isServiceRunning by ScreenTranslateService.isRunning.collectAsStateWithLifecycle()
    val pair by viewModel.languagePair.collectAsStateWithLifecycle()
    val basic by viewModel.basicModel.collectAsStateWithLifecycle()
    val highQuality by viewModel.highQualityModel.collectAsStateWithLifecycle()

    // Permissions and installed models may change while the app is in the background.
    LifecycleResumeEffect(Unit) {
        overlayGranted = Permissions.canDrawOverlays(context)
        viewModel.refreshModels()
        onPauseOrDispose { }
    }

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

    val modelSummary = when {
        highQuality.status == HighQualityModels.Status.READY -> ModelSummary.HIGH_QUALITY_READY
        basic == ModelState.READY -> ModelSummary.BASIC_READY
        highQuality.status == HighQualityModels.Status.DOWNLOADING || basic == ModelState.DOWNLOADING ->
            ModelSummary.DOWNLOADING
        else -> ModelSummary.NEEDED
    }

    HomeScreen(
        state = HomeUiState(
            isServiceRunning = isServiceRunning,
            overlayGranted = overlayGranted,
            languages = context.getString(
                R.string.translation_languages,
                LanguageNames.displayName(pair.source),
                LanguageNames.displayName(pair.target),
            ),
            modelSummary = modelSummary,
        ),
        onToggleService = {
            if (isServiceRunning) {
                ScreenTranslateService.stop(context)
            } else {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                captureConsentLauncher.launch(manager.createScreenCaptureIntent())
            }
        },
        onGrantOverlay = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        onOpenLanguages = onOpenLanguages,
        onOpenSettings = onOpenSettings,
        onOpenSupport = onOpenSupport,
    )
}
