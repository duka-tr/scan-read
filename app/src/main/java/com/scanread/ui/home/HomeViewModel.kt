package com.scanread.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scanread.ScanReadApp
import com.scanread.translate.LanguagePair
import com.scanread.translate.TranslationModelStore
import com.scanread.translate.mlkit.MlKitModelStore
import com.scanread.translate.mlkit.defaultLanguagePair
import com.scanread.translate.opusmt.HighQualityModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModelState { CHECKING, MISSING, DOWNLOADING, READY, ERROR }

/** Manages the translation model state shown on the home screen. */
class HomeViewModel(
    val languagePair: LanguagePair,
    private val modelStore: TranslationModelStore,
    private val highQualityModels: HighQualityModels,
) : ViewModel() {

    private val _modelState = MutableStateFlow(ModelState.CHECKING)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    /** The high-quality (Opus-MT) model; when ready it is used instead of ML Kit. */
    val highQuality: StateFlow<HighQualityModels.State> = highQualityModels.state

    /** Download size of the high-quality model in megabytes. */
    val highQualitySizeMb: Int = (highQualityModels.downloadSize / BYTES_PER_MB).toInt()

    init {
        refreshModelState()
    }

    fun refreshModelState() {
        highQualityModels.refresh()
        if (_modelState.value == ModelState.DOWNLOADING) return
        viewModelScope.launch {
            _modelState.value = runModelOperation(failureState = ModelState.ERROR) {
                if (modelStore.isDownloaded(languagePair)) ModelState.READY else ModelState.MISSING
            }
        }
    }

    fun downloadModel() {
        if (_modelState.value == ModelState.DOWNLOADING) return
        _modelState.value = ModelState.DOWNLOADING
        viewModelScope.launch {
            _modelState.value = runModelOperation(failureState = ModelState.ERROR) {
                modelStore.download(languagePair)
                ModelState.READY
            }
        }
    }

    fun deleteModel() {
        if (_modelState.value == ModelState.DOWNLOADING) return
        viewModelScope.launch {
            _modelState.value = runModelOperation(failureState = _modelState.value) {
                modelStore.delete(languagePair)
                ModelState.MISSING
            }
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
        private const val TAG = "HomeViewModel"
        private const val BYTES_PER_MB = 1_000_000L

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ScanReadApp
                HomeViewModel(
                    languagePair = defaultLanguagePair(),
                    modelStore = MlKitModelStore(),
                    highQualityModels = app.highQualityModels,
                )
            }
        }
    }
}
