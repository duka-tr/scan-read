package tr.duka.scanread.text

import tr.duka.scanread.text.model.TextLine
import tr.duka.scanread.text.model.TextWord
import kotlin.math.max

/**
 * Splits OCR lines that actually span two separate areas (e.g. adjacent speech balloons).
 *
 * A line is split between two words when they sit in different enclosed regions, or when the gap
 * between them is wider than [maxWordGapRatio] × word height.
 */
class LineSplitter(
    private val maxWordGapRatio: Float = DEFAULT_MAX_WORD_GAP_RATIO,
) {
    fun split(line: TextLine, regions: RegionLookup = RegionLookup.NONE): List<TextLine> {
        if (line.words.size < 2) return listOf(line)

        val segments = mutableListOf<MutableList<TextWord>>()
        var previousRegion: RegionLookup.Region? = null
        for (word in line.words.sortedBy { it.box.left }) {
            val region = regions.regionOf(word.box)
            val previous = segments.lastOrNull()?.last()
            if (previous == null || isBoundary(previous, word, previousRegion, region)) {
                segments += mutableListOf(word)
            } else {
                segments.last() += word
            }
            previousRegion = region
        }

        if (segments.size == 1) return listOf(line)
        return segments.map { words ->
            TextLine(
                text = words.joinToString(" ") { it.text },
                box = words.map(TextWord::box).reduce { acc, box -> acc.union(box) },
                words = words,
            )
        }
    }

    private fun isBoundary(
        previous: TextWord,
        next: TextWord,
        previousRegion: RegionLookup.Region?,
        nextRegion: RegionLookup.Region?,
    ): Boolean {
        if (previousRegion != null && nextRegion != null && previousRegion.id != nextRegion.id) return true

        val gap = next.box.left - previous.box.right
        val wordHeight = max(previous.box.height, next.box.height)
        return gap > wordHeight * maxWordGapRatio
    }

    private companion object {
        const val DEFAULT_MAX_WORD_GAP_RATIO = 1.0f
    }
}
