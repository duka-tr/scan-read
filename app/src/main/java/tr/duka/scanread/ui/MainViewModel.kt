package tr.duka.scanread.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tr.duka.scanread.ScanReadApp
import tr.duka.scanread.core.locale.LanguageNames
import tr.duka.scanread.core.settings.AppSettings
import tr.duka.scanread.core.settings.SettingsRepository
import tr.duka.scanread.translate.LanguagePair
import tr.duka.scanread.translate.TranslationModelStore
import tr.duka.scanread.translate.mlkit.MlKitModelStore
import tr.duka.scanread.translate.mlkit.resolveLanguagePair
import tr.duka.scanread.translate.mlkit.translatableLanguages
import tr.duka.scanread.translate.opusmt.HighQualityModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** State of the basic (ML Kit) language files for the selected pair. */
enum class ModelState { CHECKING, MISSING, DOWNLOADING, READY, ERROR }

/** A selectable language with its name in the user's language. */
data class LanguageOption(val code: String, val name: String)

/**
 * Shared state of the app's screens: settings, the selected language pair and its models.
 */
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val basicModels: TranslationModelStore,
    private val highQualityModels: HighQualityModels,
) : ViewModel() {

    private val _settings = MutableStateFlow<AppSettings?>(null)
    /** `null` until the stored settings are loaded. */
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _languagePair = MutableStateFlow(resolveLanguagePair(AppSettings()))
    val languagePair: StateFlow<LanguagePair> = _languagePair.asStateFlow()

    private val _basicModel = MutableStateFlow(ModelState.CHECKING)
    val basicModel: StateFlow<ModelState> = _basicModel.asStateFlow()

    val highQualityModel: StateFlow<HighQualityModels.State> = highQualityModels.state

    /** Alphabetical order of the user's language (e.g. Turkish "Ç" right after "C"). */
    private val nameOrder = compareBy<LanguageOption, String>(Collator.getInstance(Locale.getDefault())) { it.name }

    val sourceLanguages: List<LanguageOption> =
        LanguagePair.SOURCE_LANGUAGES.filter { it in translatableLanguages }.map(::option).sortedWith(nameOrder)

    val targetLanguages: List<LanguageOption> =
        translatableLanguages.map(::option).sortedWith(nameOrder)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { loaded ->
                _settings.value = loaded
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

    private fun option(code: String) = LanguageOption(code, LanguageNames.displayName(code))

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

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ScanReadApp
                MainViewModel(app.settings, MlKitModelStore(), app.highQualityModels)
            }
        }
    }
}
