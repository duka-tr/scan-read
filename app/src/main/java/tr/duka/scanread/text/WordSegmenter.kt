package tr.duka.scanread.text

/**
 * Splits words that OCR glued together ("withyou" → "with you", "areal" → "a real") using a
 * dictionary of frequent words with log-probability scores.
 *
 * Also repairs a doubled letter in a long unknown word ("tresspassing" → "trespassing") when
 * dropping it gives a dictionary word, possibly with a common suffix.
 *
 * Only words missing from the dictionary are considered, and only splits into a few frequent
 * words are accepted, so names and rare words stay intact.
 */
class WordSegmenter(
    private val scores: Map<String, Float>,
    /** Minimum score of a short part; short rare words are usually syllables of a name. */
    private val minShortPartScore: Float = DEFAULT_MIN_SHORT_PART_SCORE,
    /** Minimum score of a part with at least [LONG_PART_LENGTH] letters ("pirate" is rare but clear). */
    private val minLongPartScore: Float = DEFAULT_MIN_LONG_PART_SCORE,
    private val maxParts: Int = DEFAULT_MAX_PARTS,
    private val partPenalty: Float = DEFAULT_PART_PENALTY,
) {
    /** Returns [text] with glued words separated and doubled letters repaired; everything else is unchanged. */
    fun segment(text: String): String = WORD.replace(text) { match ->
        val word = match.value
        repairDoubledLetter(word.lowercase())?.let { return@replace withCaseOf(word, it) }
        val parts = split(word.lowercase()) ?: return@replace word
        parts.mapIndexed { index, part ->
            when {
                part == "i" -> "I"
                index == 0 && word.first().isUpperCase() -> part.replaceFirstChar(Char::uppercaseChar)
                else -> part
            }
        }.joinToString(" ")
    }

    /** Best split of [word] into dictionary words, or `null` if it is known or cannot be split well. */
    internal fun split(word: String): List<String>? {
        if (word.length < MIN_WORD_LENGTH || word in scores) return null

        val length = word.length
        val best = arrayOfNulls<Pair<Float, List<String>>>(length + 1)
        best[0] = 0f to emptyList()
        for (start in 0 until length) {
            val (score, parts) = best[start] ?: continue
            if (parts.size >= maxParts) continue
            for (end in start + 1..length) {
                val part = word.substring(start, end)
                if (!isAllowedPart(part)) continue
                // "a"/"i" glued to a short word is more likely a name's syllable ("i-chi-go").
                if (parts.lastOrNull()?.length == 1 && part.length < LONG_AFTER_SINGLE_LETTER) continue
                val partScore = scores[part] ?: continue
                val minScore = if (part.length >= LONG_PART_LENGTH) minLongPartScore else minShortPartScore
                if (partScore < minScore) continue
                val candidate = score + partScore - partPenalty
                val current = best[end]
                if (current == null || candidate > current.first) best[end] = candidate to parts + part
            }
        }
        return best[length]?.second?.takeIf { it.size > 1 }
    }

    /** [word] without one of its doubled letters, if that makes it a known word; otherwise `null`. */
    internal fun repairDoubledLetter(word: String): String? {
        if (word.length < MIN_REPAIR_LENGTH || word in scores) return null
        for (i in 1 until word.length) {
            if (word[i] != word[i - 1]) continue
            val candidate = word.removeRange(i, i + 1)
            if (isKnownForm(candidate)) return candidate
        }
        return null
    }

    private fun isKnownForm(word: String): Boolean =
        (scores[word] ?: Float.NEGATIVE_INFINITY) >= minLongPartScore || knownStem(word)

    private fun knownStem(word: String): Boolean = SUFFIXES.any { suffix ->
        if (!word.endsWith(suffix)) return@any false
        val stem = word.dropLast(suffix.length)
        stem.length >= MIN_STEM_LENGTH &&
            (listOf(stem, stem + "e").any { (scores[it] ?: Float.NEGATIVE_INFINITY) >= MIN_STEM_SCORE })
    }

    private fun withCaseOf(original: String, word: String): String = when {
        original.all { !it.isLowerCase() } -> word.uppercase()
        original.first().isUpperCase() -> word.replaceFirstChar(Char::uppercaseChar)
        else -> word
    }

    private fun isAllowedPart(part: String): Boolean = when (part.length) {
        1 -> part in SINGLE_LETTER_WORDS
        2 -> part in TWO_LETTER_WORDS
        else -> true
    }

    companion object {
        private const val DEFAULT_MIN_SHORT_PART_SCORE = -11f
        private const val DEFAULT_MIN_LONG_PART_SCORE = -12.5f
        private const val LONG_PART_LENGTH = 5
        private const val LONG_AFTER_SINGLE_LETTER = 4
        private const val DEFAULT_MAX_PARTS = 3
        private const val DEFAULT_PART_PENALTY = 2.5f
        private const val MIN_WORD_LENGTH = 3
        /** Shorter words with a doubled letter are often names ("Anna", "Mattie"). */
        private const val MIN_REPAIR_LENGTH = 7
        private const val MIN_STEM_LENGTH = 4
        private const val MIN_STEM_SCORE = -14f
        private val SUFFIXES = listOf("ing", "ed", "es", "s", "er", "ers", "ly")

        private val WORD = Regex("\\b[A-Za-z]+\\b")
        private val SINGLE_LETTER_WORDS = setOf("a", "i")

        /** Two-letter parts must be real words; otherwise names split into syllables ("na mi"). */
        private val TWO_LETTER_WORDS = setOf(
            "am", "an", "as", "at", "be", "by", "do", "go", "he", "if", "in", "is", "it", "me",
            "my", "no", "of", "oh", "ok", "on", "or", "so", "to", "up", "us", "we",
        )
    }
}
