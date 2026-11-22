package tr.duka.scanread.translate

/**
 * Source and target language as ISO 639-1 codes (e.g. "en", "tr").
 */
data class LanguagePair(
    val source: String,
    val target: String,
) {
    companion object {
        const val DEFAULT_SOURCE = "en"

        /** Used when the device language cannot be a target. */
        const val FALLBACK_TARGET = "es"

        /**
         * Languages that can be read from the screen: Latin-script languages supported by the
         * bundled text recognizer and by translation. Other scripts need their own OCR models.
         */
        val SOURCE_LANGUAGES = listOf(
            "en", "es", "fr", "de", "it", "pt", "nl", "id", "pl", "tr",
            "sv", "da", "ro", "cs", "hu", "fi", "ca", "hr", "sk",
        )

        /** Translates from [DEFAULT_SOURCE] into the device language when it is supported. */
        fun forDevice(deviceLanguage: String, supportedLanguages: Set<String>): LanguagePair =
            resolve(null, null, deviceLanguage, supportedLanguages)

        /**
         * The pair to use: the user's choice where valid, otherwise [DEFAULT_SOURCE] into the device
         * language (or [FALLBACK_TARGET] when that is not possible).
         */
        fun resolve(
            source: String?,
            target: String?,
            deviceLanguage: String,
            supportedLanguages: Set<String>,
        ): LanguagePair {
            val resolvedSource = source?.takeIf { it in SOURCE_LANGUAGES } ?: DEFAULT_SOURCE
            val resolvedTarget = target?.takeIf { it in supportedLanguages && it != resolvedSource }
                ?: deviceLanguage.lowercase().takeIf { it in supportedLanguages && it != resolvedSource }
                ?: FALLBACK_TARGET.takeIf { it != resolvedSource }
                ?: DEFAULT_SOURCE
            return LanguagePair(resolvedSource, resolvedTarget)
        }
    }
}
