package tr.duka.scanread.core.locale

import java.util.Locale

/** Human-readable language names in the user's language. */
object LanguageNames {

    /** e.g. "en" → "English" / "İngilizce". */
    fun displayName(code: String, displayLocale: Locale = Locale.getDefault()): String =
        Locale.forLanguageTag(code)
            .getDisplayLanguage(displayLocale)
            .replaceFirstChar { it.titlecase(displayLocale) }
}
