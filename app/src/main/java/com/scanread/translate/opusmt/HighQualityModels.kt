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
 * App-wide state of the high-quality (Opus-MT) model for the selected language pair. Downloads
 * run in an application-lifetime [scope], so they continue when the user leaves the screen.
 */
class HighQualityModels(
    private val store: OpusMtModelStore,
    private val scope: CoroutineScope,
) {
    enum class Status { UNSUPPORTED, CHECKING, MISSING, DOWNLOADING, READY, ERROR }

    /**
     * @property progress download progress, 0..1 (only meaningful while downloading).
     * @property downloadSize size of the model in bytes, 0 when the pair has none.
     */
    data class State(val status: Status, val progress: Float = 0f, val downloadSize: Long = 0L)

    private val _state = MutableStateFlow(State(Status.UNSUPPORTED))
    val state: StateFlow<State> = _state.asStateFlow()

    private var pair: LanguagePair? = null
    private var job: Job? = null

    /** Follows the selected language pair; a download for another pair is paused (it can resume). */
    fun select(newPair: LanguagePair) {
        if (newPair == pair) return
        job?.cancel()
        pair = newPair
        val size = OpusMtCatalog.entry(newPair)?.totalSize ?: 0L
        _state.value = State(if (size > 0) Status.CHECKING else Status.UNSUPPORTED, downloadSize = size)
        refresh()
    }

    fun refresh() {
        val current = pair ?: return
        if (_state.value.status == Status.UNSUPPORTED || job?.isActive == true) return
        scope.launch {
            val ready = store.isDownloaded(current)
            if (current == pair) _state.value = _state.value.copy(status = if (ready) Status.READY else Status.MISSING)
        }
    }

    fun download() {
        val current = pair ?: return
        if (_state.value.status == Status.UNSUPPORTED || job?.isActive == true) return
        _state.value = _state.value.copy(status = Status.DOWNLOADING, progress = 0f)
        job = scope.launch {
            val result = try {
                store.download(current) { progress ->
                    // Publish whole percents only; the downloader reports every chunk.
                    val rounded = (progress * PERCENT).toInt() / PERCENT
                    if (current == pair && rounded != _state.value.progress) {
                        _state.value = _state.value.copy(status = Status.DOWNLOADING, progress = rounded)
                    }
                }
                Status.READY
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "High-quality model download failed", e)
                Status.ERROR
            }
            if (current == pair) _state.value = _state.value.copy(status = result, progress = 0f)
        }
    }

    fun delete() {
        val current = pair ?: return
        if (job?.isActive == true) return
        scope.launch {
            store.delete(current)
            if (current == pair) _state.value = _state.value.copy(status = Status.MISSING)
        }
    }

    private companion object {
        const val TAG = "HighQualityModels"
        const val PERCENT = 100f
    }
}
