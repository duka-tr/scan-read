package tr.duka.scanread.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.duka.scanread.R
import tr.duka.scanread.core.locale.LanguageNames
import tr.duka.scanread.core.permission.Permissions
import tr.duka.scanread.service.ScreenTranslateService
import tr.duka.scanread.translate.opusmt.HighQualityModels
import tr.duka.scanread.ui.MainViewModel
import tr.duka.scanread.ui.ModelState
import tr.duka.scanread.ui.UpdateState

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
    val update by viewModel.update.collectAsStateWithLifecycle()

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

    val locale = LocalConfiguration.current.locales[0]
    HomeScreen(
        state = HomeUiState(
            isServiceRunning = isServiceRunning,
            overlayGranted = overlayGranted,
            languages = context.getString(
                R.string.translation_languages,
                LanguageNames.displayName(pair.source, locale),
                LanguageNames.displayName(pair.target, locale),
            ),
            modelSummary = modelSummary,
            availableUpdate = (update as? UpdateState.Available)?.release,
        ),
        onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
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
