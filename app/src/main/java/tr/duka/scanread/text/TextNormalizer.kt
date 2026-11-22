package tr.duka.scanread.text

import java.util.Locale

/**
 * Cleans up recognized text before translation. Casing rules assume a Latin-script source language.
 */
object TextNormalizer {

    private const val ALL_CAPS_THRESHOLD = 0.8
    private const val MIN_LETTERS_FOR_CAPS = 2
    private val WHITESPACE = Regex("\\s+")
    private val STANDALONE_I = Regex("\\bi\\b")
    private val TITLE_ABBREVIATION = Regex("\\b(?:mr|mrs|ms|dr|prof)\\.", RegexOption.IGNORE_CASE)
    private const val SENTENCE_END = ".!?"

    private val WORD = Regex("\\b[A-Za-z]+\\b")
    /** Apostrophe-less contractions [repairContractions] understands, e.g. "youve". */
    val contractionKeys: Set<String> get() = CONTRACTIONS.keys

    private val CONTRACTIONS = mapOf(
        "im" to "I'm", "ive" to "I've", "youre" to "you're", "youve" to "you've", "youll" to "you'll",
        "youd" to "you'd", "hes" to "he's", "shes" to "she's", "thats" to "that's", "theres" to "there's",
        "whats" to "what's", "whatre" to "what're", "wheres" to "where's", "whos" to "who's",
        "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll", "weve" to "we've",
        "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't",
        "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "isnt" to "isn't",
        "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't", "havent" to "haven't",
        "hasnt" to "hasn't", "hadnt" to "hadn't", "wont" to "won't", "aint" to "ain't",
    )

    /** Characters OCR produces from frame or balloon edges next to the text. */
    private val EDGE_ARTIFACTS = charArrayOf('|', '¦')

    /**
     * Joins lines with spaces. A hyphen at the end of a line followed by a letter is treated as a
     * word break ("SOME-" + "THING" → "SOMETHING").
     */
    fun joinLines(lines: List<String>): String {
        val result = StringBuilder()
        val cleaned = lines.map { it.trim().trim(*EDGE_ARTIFACTS).trim() }
        for (line in cleaned.filter(String::isNotEmpty)) {
            if (result.isNotEmpty()) {
                if (endsWithWordBreak(result) && line.first().isLetter()) {
                    result.setLength(result.length - 1)
                } else {
                    result.append(' ')
                }
            }
            result.append(line)
        }
        return result.toString().replace(WHITESPACE, " ").trim()
    }

    /**
     * Restores apostrophes OCR commonly drops from English contractions ("Ive" → "I've",
     * "dont" → "don't"). Ambiguous forms that are also words ("were", "well", "its", "ill") are kept.
     */
    fun repairContractions(text: String): String = WORD.replace(text) { match ->
        val word = match.value
        val repaired = CONTRACTIONS[word.lowercase(Locale.ROOT)] ?: return@replace word
        when {
            repaired.startsWith("I'") -> repaired
            word.first().isUpperCase() -> repaired.replaceFirstChar(Char::uppercaseChar)
            else -> repaired
        }
    }

    /** True when at least 80% of the letters are capitals. */
    fun isAllCaps(text: String): Boolean {
        val letters = text.filter(Char::isLetter)
        if (letters.length < MIN_LETTERS_FOR_CAPS) return false
        return letters.count(Char::isUpperCase) >= letters.length * ALL_CAPS_THRESHOLD
    }

    /** "HEY! WHERE ARE YOU GOING?" → "Hey! Where are you going?" */
    fun toSentenceCase(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val result = StringBuilder(lower.length)
        var capitalizeNext = true
        for (char in lower) {
            if (capitalizeNext && char.isLetter()) {
                result.append(char.uppercaseChar())
                capitalizeNext = false
            } else {
                result.append(char)
            }
            if (char in SENTENCE_END) capitalizeNext = true
        }
        val withPronoun = STANDALONE_I.replace(result, "I")
        // Title abbreviations are capitalized wherever they appear ("hey, mr. Smith" → "hey, Mr. Smith").
        return TITLE_ABBREVIATION.replace(withPronoun) { match -> match.value.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun endsWithWordBreak(text: CharSequence): Boolean =
        text.length >= 2 && text[text.length - 1] == '-' && text[text.length - 2].isLetter()
}
