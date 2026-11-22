package com.scanread.translate

/** Manages the on-device language models a [TranslationEngine] needs. */
interface TranslationModelStore {
    suspend fun isDownloaded(pair: LanguagePair): Boolean

    suspend fun download(pair: LanguagePair)

    /** Deletes the target-language model; the source model may be shared with other pairs. */
    suspend fun delete(pair: LanguagePair)
}
