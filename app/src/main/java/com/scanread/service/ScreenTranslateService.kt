package com.scanread.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.scanread.BuildConfig
import com.scanread.R
import com.scanread.capture.ScreenCapturer
import com.scanread.capture.SystemBarAreas
import com.scanread.core.notification.NotificationChannels
import com.scanread.core.permission.Permissions
import com.scanread.ocr.MlKitOcrEngine
import com.scanread.overlay.FloatingButton
import com.scanread.overlay.TranslationOverlay
import com.scanread.overlay.TranslationRenderer
import com.scanread.pipeline.BlockTranslator
import com.scanread.pipeline.DebugCaptureDump
import com.scanread.pipeline.ScreenTextAnalyzer
import com.scanread.render.SoundEffectFilter
import com.scanread.text.TextNormalizer
import com.scanread.text.WordSegmenter
import com.scanread.translate.opusmt.OpusMtBundle
import com.scanread.translate.CachingTranslationEngine
import com.scanread.translate.LanguagePair
import com.scanread.translate.TranslationEngine
import com.scanread.translate.TranslationBackends
import com.scanread.translate.TranslationModelStore
import com.scanread.translate.mlkit.MlKitModelStore
import com.scanread.translate.mlkit.defaultLanguagePair
import com.scanread.ui.MainActivity
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns the screen-capture session and the floating button.
 *
 * Start it with [start] after the user has granted screen-capture consent.
 */
class ScreenTranslateService : LifecycleService() {

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var analyzer: ScreenTextAnalyzer? = null
    private var translationEngine: TranslationEngine? = null
    private var blockTranslator: BlockTranslator? = null
    private var floatingButton: FloatingButton? = null
    private var translationOverlay: TranslationOverlay? = null
    private var captureJob: Job? = null

    private val languagePair: LanguagePair by lazy { defaultLanguagePair() }
    private val modelStore: TranslationModelStore = MlKitModelStore()
    private val soundEffectFilter = SoundEffectFilter()
    private var backend = TranslationBackends.Backend.ML_KIT
    private var isModelReady = false

    private val projectionCallback = object : MediaProjection.Callback() {
        // Called when the user revokes capture (e.g. from the system status bar chip).
        override fun onStop() = stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopSelf()
            // Restarted by the system without capture consent: nothing to resume.
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (projection != null) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || resultData == null || !Permissions.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        // Must be in the foreground before obtaining the MediaProjection (Android 10+).
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, resultData) ?: run {
            stopSelf()
            return
        }
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = mediaProjection

        capturer = ScreenCapturer(this, mediaProjection).also { it.start() }
        analyzer = ScreenTextAnalyzer(MlKitOcrEngine())
        val (selectedBackend, baseEngine) = TranslationBackends.createEngine(this, languagePair)
        backend = selectedBackend
        val engine = CachingTranslationEngine(baseEngine)
        translationEngine = engine
        // The Opus-MT vocabulary doubles as a dictionary for splitting words OCR glued together.
        val segmenter = OpusMtBundle.installed(filesDir, languagePair)
            ?.takeIf { selectedBackend == TranslationBackends.Backend.OPUS_MT }
            ?.let { bundle ->
                lazy {
                    val contractions = TextNormalizer.contractionKeys.associateWith { CONTRACTION_WORD_SCORE }
                    WordSegmenter(contractions + bundle.loadWordScores())
                }
            }
        blockTranslator = BlockTranslator(engine, Locale.forLanguageTag(languagePair.target)) { text ->
            segmenter?.value?.segment(text)?.let(TextNormalizer::repairContractions) ?: text
        }
        translationOverlay = TranslationOverlay(this, onDismissRequest = ::dismissOverlay)
        floatingButton = FloatingButton(this, onClick = ::onFloatingButtonClicked).also { it.show() }
        _isRunning.value = true
    }

    private fun onFloatingButtonClicked() {
        // The button toggles: a second tap removes the current result.
        if (translationOverlay?.isShowing == true) {
            dismissOverlay()
            return
        }
        if (captureJob?.isActive == true) return
        captureJob = lifecycleScope.launch { captureAndTranslate() }
    }

    private suspend fun ensureModelReady(): Boolean {
        // An installed Opus-MT bundle is complete by definition (checked when the engine was chosen).
        if (backend == TranslationBackends.Backend.OPUS_MT) return true
        if (!isModelReady) {
            isModelReady = runCatching { modelStore.isDownloaded(languagePair) }
                .onFailure { Log.w(TAG, "Could not check translation models", it) }
                .getOrDefault(false)
        }
        return isModelReady
    }

    private suspend fun captureAndTranslate() {
        val button = floatingButton ?: return
        val screenAnalyzer = analyzer ?: return
        val translator = blockTranslator ?: return

        if (!ensureModelReady()) {
            showToast(getString(R.string.translation_model_required))
            return
        }

        // Hide our own UI and wait for a fresh frame so it is not part of the capture.
        button.setHidden(true)
        delay(CAPTURE_SETTLE_DELAY_MS)
        val bitmap = withContext(Dispatchers.Default) { capturer?.captureLatest() }
        button.setHidden(false)

        if (bitmap == null) {
            showToast(getString(R.string.capture_failed))
            return
        }

        button.setBusy(true)
        try {
            val ignoredAreas = SystemBarAreas.current(this)
            val startedAt = SystemClock.elapsedRealtime()

            val analysis = runStep(R.string.ocr_failed) { screenAnalyzer.analyze(bitmap, ignoredAreas) } ?: return
            if (analysis.blocks.isEmpty()) {
                showToast(getString(R.string.ocr_no_text_found))
                return
            }
            val translated = runStep(R.string.translation_failed) { translator.translate(analysis.blocks) } ?: return
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt

            if (BuildConfig.DEBUG) {
                withContext(Dispatchers.IO) {
                    DebugCaptureDump(this@ScreenTranslateService)
                        .write(bitmap, ignoredAreas, analysis, translated, elapsedMs)
                }
            }
            val soundEffects = soundEffectFilter.detect(
                blocks = analysis.blocks,
                translations = translated.map { it.translation },
                inBalloon = analysis.surfaces.map { it.isShaped },
            )
            translationOverlay?.show(
                translated.indices.filterNot { soundEffects[it] }.map { index ->
                    val block = translated[index]
                    TranslationRenderer.Item(
                        text = block.displayText,
                        surface = analysis.surfaces[index],
                        originalLineHeight = block.block.box.height.toFloat() / block.block.lines.size,
                    )
                },
            )
        } finally {
            bitmap.recycle()
            button.setBusy(false)
        }
    }

    /** Runs a pipeline step; on failure logs it, shows [errorMessage] and returns `null`. */
    private suspend fun <T> runStep(@StringRes errorMessage: Int, step: suspend () -> T): T? = try {
        step()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Pipeline step failed", e)
        showToast(getString(errorMessage))
        null
    }

    private fun dismissOverlay() {
        translationOverlay?.dismiss()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Block positions no longer match the screen after a rotation.
        dismissOverlay()
        capturer?.onScreenSizeChanged()
    }

    override fun onDestroy() {
        captureJob?.cancel()
        translationOverlay?.dismiss()
        translationOverlay = null
        floatingButton?.remove()
        floatingButton = null
        analyzer?.close()
        analyzer = null
        blockTranslator = null
        translationEngine?.close()
        translationEngine = null
        capturer?.release()
        capturer = null
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        _isRunning.value = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, ScreenTranslateService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NotificationChannels.SCREEN_TRANSLATION)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "ScreenTranslateService"
        private const val ACTION_START = "com.scanread.action.START"
        private const val ACTION_STOP = "com.scanread.action.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_ID = 1
        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_STOP = 1
        private const val CAPTURE_SETTLE_DELAY_MS = 150L

        /** Score for apostrophe-less contractions ("youve") so glued words can split into them. */
        private const val CONTRACTION_WORD_SCORE = -7f

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** Starts the service with the result of [MediaProjectionManager.createScreenCaptureIntent]. */
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenTranslateService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenTranslateService::class.java))
        }
    }
}
