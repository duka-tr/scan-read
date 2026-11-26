package tr.duka.scanread.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tr.duka.scanread.BuildConfig
import tr.duka.scanread.ScanReadApp
import tr.duka.scanread.core.settings.AppSettings
import tr.duka.scanread.core.settings.SettingsRepository
import tr.duka.scanread.translate.LanguagePair
import tr.duka.scanread.translate.TranslationModelStore
import tr.duka.scanread.translate.mlkit.MlKitModelStore
import tr.duka.scanread.translate.mlkit.resolveLanguagePair
import tr.duka.scanread.translate.mlkit.translatableLanguages
import tr.duka.scanread.translate.opusmt.HighQualityModels
import tr.duka.scanread.update.AppRelease
import tr.duka.scanread.update.AppVersion
import tr.duka.scanread.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State of the basic (ML Kit) language files for the selected pair. */
enum class ModelState { CHECKING, MISSING, DOWNLOADING, READY, ERROR }

/** Update check state for builds distributed outside Google Play. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
    data class Available(val release: AppRelease) : UpdateState
}


/**
 * Shared state of the app's screens: settings, the selected language pair and its models.
 */
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val basicModels: TranslationModelStore,
    private val highQualityModels: HighQualityModels,
    private val updateChecker: UpdateChecker?,
) : ViewModel() {

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    /** Newer app version, when one is found; always idle in Play builds. */
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** Whether this build can check for updates at all (not in Play builds). */
    val canCheckUpdates: Boolean get() = updateChecker != null
    private var autoCheckDone = false

    private val _settings = MutableStateFlow<AppSettings?>(null)
    /** `null` until the stored settings are loaded. */
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _languagePair = MutableStateFlow(resolveLanguagePair(AppSettings()))
    val languagePair: StateFlow<LanguagePair> = _languagePair.asStateFlow()

    private val _basicModel = MutableStateFlow(ModelState.CHECKING)
    val basicModel: StateFlow<ModelState> = _basicModel.asStateFlow()

    val highQualityModel: StateFlow<HighQualityModels.State> = highQualityModels.state

    /** Codes of the languages that can be read; the screen names and sorts them in its own language. */
    val sourceLanguages: List<String> = LanguagePair.SOURCE_LANGUAGES.filter { it in translatableLanguages }

    /** Codes of the languages that can be translated into. */
    val targetLanguages: List<String> = translatableLanguages.toList()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { loaded ->
                _settings.value = loaded
                maybeAutoCheckForUpdates(loaded)
                val pair = resolveLanguagePair(loaded)
                if (pair != _languagePair.value || _basicModel.value == ModelState.CHECKING) {
                    _languagePair.value = pair
                    highQualityModels.select(pair)
                    refreshModels()
                }
            }
        }
    }

    fun refreshModels() {
        highQualityModels.refresh()
        if (_basicModel.value == ModelState.DOWNLOADING) return
        val pair = _languagePair.value
        viewModelScope.launch {
            val state = runModelOperation(ModelState.ERROR) {
                if (basicModels.isDownloaded(pair)) ModelState.READY else ModelState.MISSING
            }
            if (pair == _languagePair.value) _basicModel.value = state
        }
    }

    fun setLanguages(source: String, target: String) {
        viewModelScope.launch { settingsRepository.setLanguages(source, target) }
    }

    fun setButtonSize(size: AppSettings.ButtonSize) {
        viewModelScope.launch { settingsRepository.setButtonSize(size) }
    }

    fun setButtonOpacity(opacity: Float) {
        viewModelScope.launch { settingsRepository.setButtonOpacity(opacity) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setTextScale(scale) }
    }

    fun setSkipSoundEffects(skip: Boolean) {
        viewModelScope.launch { settingsRepository.setSkipSoundEffects(skip) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckUpdates(enabled) }
    }

    /** Checks GitHub for a newer version now (user request). */
    fun checkForUpdates() {
        val checker = updateChecker ?: return
        if (_update.value == UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = when (val result = checker.check()) {
                is UpdateChecker.Result.Available -> UpdateState.Available(result.release)
                UpdateChecker.Result.UpToDate -> UpdateState.UpToDate
                UpdateChecker.Result.Failed -> UpdateState.Failed
            }
        }
    }

    /** At most once a day, when enabled; a silent failure leaves the state idle. */
    private fun maybeAutoCheckForUpdates(settings: AppSettings) {
        val checker = updateChecker ?: return
        if (autoCheckDone || !settings.autoCheckUpdates) return
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < AUTO_CHECK_INTERVAL_MS) return
        autoCheckDone = true
        viewModelScope.launch {
            val result = checker.check()
            if (result is UpdateChecker.Result.Available) _update.value = UpdateState.Available(result.release)
            if (result != UpdateChecker.Result.Failed) settingsRepository.setLastUpdateCheck(now)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    fun downloadBasicModel() {
        if (_basicModel.value == ModelState.DOWNLOADING) return
        val pair = _languagePair.value
        _basicModel.value = ModelState.DOWNLOADING
        viewModelScope.launch {
            val state = runModelOperation(ModelState.ERROR) {
                basicModels.download(pair)
                ModelState.READY
            }
            if (pair == _languagePair.value) _basicModel.value = state
        }
    }

    fun deleteBasicModel() {
        if (_basicModel.value == ModelState.DOWNLOADING) return
        val pair = _languagePair.value
        viewModelScope.launch {
            val state = runModelOperation(_basicModel.value) {
                basicModels.delete(pair)
                ModelState.MISSING
            }
            if (pair == _languagePair.value) _basicModel.value = state
        }
    }

    fun downloadHighQualityModel() = highQualityModels.download()

    fun deleteHighQualityModel() = highQualityModels.delete()

    private suspend fun runModelOperation(failureState: ModelState, operation: suspend () -> ModelState): ModelState =
        try {
            operation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Translation model operation failed", e)
            failureState
        }

    companion object {
        private const val TAG = "MainViewModel"
        private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ScanReadApp
                val updateChecker = if (BuildConfig.UPDATE_CHECK_ENABLED) {
                    AppVersion.parse(BuildConfig.VERSION_NAME)?.let { UpdateChecker(BuildConfig.RELEASES_API_URL, it) }
                } else {
                    null
                }
                MainViewModel(app.settings, MlKitModelStore(), app.highQualityModels, updateChecker)
            }
        }
    }
}
