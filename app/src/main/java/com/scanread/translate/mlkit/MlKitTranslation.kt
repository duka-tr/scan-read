package com.scanread.translate.mlkit

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.scanread.core.tasks.await
import com.scanread.translate.LanguagePair
import com.scanread.translate.TranslationEngine
import com.scanread.translate.TranslationModelStore
import java.util.Locale

/** Language pair used by the app: English into the device language. */
fun defaultLanguagePair(locale: Locale = Locale.getDefault()): LanguagePair =
    LanguagePair.forDevice(
        deviceLanguage = locale.toLanguageTag().substringBefore('-'),
        supportedLanguages = TranslateLanguage.getAllLanguages().toSet(),
    )

/** [TranslationEngine] backed by ML Kit on-device translation. */
class MlKitTranslationEngine(pair: LanguagePair) : TranslationEngine {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(pair.source.toMlKitLanguage())
            .setTargetLanguage(pair.target.toMlKitLanguage())
            .build(),
    )

    override suspend fun translate(texts: List<String>): List<String> =
        texts.map { translator.translate(it).await() }

    override fun close() = translator.close()
}

/** [TranslationModelStore] backed by ML Kit's downloadable translation models. */
class MlKitModelStore : TranslationModelStore {

    private val manager = RemoteModelManager.getInstance()

    override suspend fun isDownloaded(pair: LanguagePair): Boolean =
        listOf(pair.source, pair.target).all { manager.isModelDownloaded(model(it)).await() }

    override suspend fun download(pair: LanguagePair) {
        val conditions = DownloadConditions.Builder().build()
        listOf(pair.source, pair.target).forEach { manager.download(model(it), conditions).await() }
    }

    override suspend fun delete(pair: LanguagePair) {
        manager.deleteDownloadedModel(model(pair.target)).await()
    }

    private fun model(language: String) = TranslateRemoteModel.Builder(language.toMlKitLanguage()).build()
}

private fun String.toMlKitLanguage(): String =
    requireNotNull(TranslateLanguage.fromLanguageTag(this)) { "Unsupported language: $this" }
