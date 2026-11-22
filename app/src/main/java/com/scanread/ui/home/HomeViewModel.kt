package com.scanread.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scanread.translate.LanguagePair
import com.scanread.translate.TranslationModelStore
import com.scanread.translate.mlkit.MlKitModelStore
import com.scanread.translate.mlkit.defaultLanguagePair
import com.scanread.translate.opusmt.OpusMtBundle
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
    private val isHighQualityModelInstalled: () -> Boolean,
) : ViewModel() {

    private val _modelState = MutableStateFlow(ModelState.CHECKING)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _highQualityInstalled = MutableStateFlow(false)
    /** The high-quality (Opus-MT) model is installed and will be used instead of ML Kit. */
    val highQualityInstalled: StateFlow<Boolean> = _highQualityInstalled.asStateFlow()

    init {
        refreshModelState()
    }

    fun refreshModelState() {
        _highQualityInstalled.value = isHighQualityModelInstalled()
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

        val Factory = viewModelFactory {
            initializer {
                val filesDir = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]).filesDir
                val pair = defaultLanguagePair()
                HomeViewModel(
                    languagePair = pair,
                    modelStore = MlKitModelStore(),
                    isHighQualityModelInstalled = { OpusMtBundle.installed(filesDir, pair) != null },
                )
            }
        }
    }
}
