package com.scanread.render

import com.scanread.text.model.TextBlock
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Recognizes sound effects and other drawn lettering ("BLAM", "BOOM BOOM", a logo on a shirt)
 * that should stay untouched. Only text outside speech balloons is considered:
 *
 * - lettering much taller than the page's typical line height, or
 * - a single word that the translation engine leaves practically unchanged.
 */
class SoundEffectFilter(
    private val maxLineHeightRatio: Float = 1.6f,
    /** Longer phrases often contain names ("Mr. Smith!"), which also translate almost unchanged. */
    private val maxWords: Int = 1,
    private val minSimilarity: Float = 0.5f,
    /**
     * Large lettering is a sound effect only when it is (nearly) one repeated word ("BOOM BOOM BOOM",
     * "BAM BAM"); shouted dialogue in big letters has more distinct words.
     */
    private val maxDistinctWordsInLargeLettering: Int = 2,
) {
    /** @return `true` for each block that should be skipped, in the same order as [blocks]. */
    fun detect(blocks: List<TextBlock>, translations: List<String>, inBalloon: List<Boolean>): List<Boolean> {
        val typicalLineHeight = median(blocks.map(::lineHeight))
        return blocks.indices.map { index ->
            val block = blocks[index]
            when {
                inBalloon[index] -> false
                typicalLineHeight > 0 && lineHeight(block) > typicalLineHeight * maxLineHeightRatio &&
                    distinctWords(block.sourceText) <= maxDistinctWordsInLargeLettering -> true
                block.sourceText.split(' ').size <= maxWords &&
                    similarity(block.sourceText, translations[index]) >= minSimilarity -> true
                else -> false
            }
        }
    }

    private fun lineHeight(block: TextBlock): Float = block.box.height.toFloat() / block.lines.size

    private fun distinctWords(text: String): Int =
        text.split(' ').map(::lettersOnly).filter(String::isNotEmpty).toSet().size

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    companion object {
        private val MARKS = Regex("\\p{M}+")

        /** 1 − normalized edit distance between the letters of [a] and [b], ignoring case and accents. */
        fun similarity(a: String, b: String): Float {
            val x = lettersOnly(a)
            val y = lettersOnly(b)
            if (x.isEmpty() || y.isEmpty()) return 0f
            return 1f - editDistance(x, y).toFloat() / max(x.length, y.length)
        }

        internal fun lettersOnly(text: String): String =
            MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
                .uppercase(Locale.ROOT)
                .filter(Char::isLetter)

        private fun editDistance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                }
                previous = current
            }
            return previous[b.length]
        }
    }
}
