package com.scanread.translate

/** Manages the on-device language models a [TranslationEngine] needs. */
interface TranslationModelStore {
    suspend fun isDownloaded(pair: LanguagePair): Boolean

    /**
     * Downloads the models for [pair].
     *
     * @param onProgress share completed, 0..1, when the store can report it.
     */
    suspend fun download(pair: LanguagePair, onProgress: (Float) -> Unit = {})

    /** Deletes the target-language model; the source model may be shared with other pairs. */
    suspend fun delete(pair: LanguagePair)
}
