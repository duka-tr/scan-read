package com.scanread.translate

/**
 * Source and target language as ISO 639-1 codes (e.g. "en", "tr").
 */
data class LanguagePair(
    val source: String,
    val target: String,
) {
    companion object {
        const val DEFAULT_SOURCE = "en"

        /** Used when the device language cannot be a target, until language selection exists. */
        const val FALLBACK_TARGET = "es"

        /** Translates from [DEFAULT_SOURCE] into the device language when it is supported. */
        fun forDevice(deviceLanguage: String, supportedLanguages: Set<String>): LanguagePair {
            val target = deviceLanguage.lowercase()
                .takeIf { it in supportedLanguages && it != DEFAULT_SOURCE }
                ?: FALLBACK_TARGET
            return LanguagePair(DEFAULT_SOURCE, target)
        }
    }
}
