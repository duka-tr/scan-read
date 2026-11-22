package com.scanread.translate.opusmt

import android.util.Log
import com.scanread.translate.LanguagePair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide state of the high-quality (Opus-MT) model for [pair]. Downloads run in an
 * application-lifetime [scope], so they continue when the user leaves the screen.
 */
class HighQualityModels(
    private val store: OpusMtModelStore,
    val pair: LanguagePair,
    private val scope: CoroutineScope,
) {
    enum class Status { UNSUPPORTED, CHECKING, MISSING, DOWNLOADING, READY, ERROR }

    /** @property progress download progress, 0..1 (only meaningful while downloading). */
    data class State(val status: Status, val progress: Float = 0f)

    /** Download size of the model in bytes, or 0 if the pair has no high-quality model. */
    val downloadSize: Long = OpusMtCatalog.entry(pair)?.totalSize ?: 0L

    private val _state = MutableStateFlow(State(if (downloadSize > 0) Status.CHECKING else Status.UNSUPPORTED))
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (_state.value.status == Status.UNSUPPORTED || job?.isActive == true) return
        scope.launch {
            _state.value = State(if (store.isDownloaded(pair)) Status.READY else Status.MISSING)
        }
    }

    fun download() {
        if (_state.value.status == Status.UNSUPPORTED || job?.isActive == true) return
        _state.value = State(Status.DOWNLOADING)
        job = scope.launch {
            _state.value = try {
                store.download(pair) { progress ->
                    // Publish whole percents only; the downloader reports every chunk.
                    val rounded = (progress * PERCENT).toInt() / PERCENT
                    if (rounded != _state.value.progress) _state.value = State(Status.DOWNLOADING, rounded)
                }
                State(Status.READY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "High-quality model download failed", e)
                State(Status.ERROR)
            }
        }
    }

    fun delete() {
        if (job?.isActive == true) return
        scope.launch {
            store.delete(pair)
            _state.value = State(Status.MISSING)
        }
    }

    private companion object {
        const val TAG = "HighQualityModels"
        const val PERCENT = 100f
    }
}
