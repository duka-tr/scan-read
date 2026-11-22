package tr.duka.scanread.translate

import java.io.Closeable

/** Translates text for a fixed language pair. Implementations must run fully on-device. */
interface TranslationEngine : Closeable {

    /** Translates [texts] and returns the results in the same order. */
    suspend fun translate(texts: List<String>): List<String>
}

suspend fun TranslationEngine.translate(text: String): String = translate(listOf(text)).single()
