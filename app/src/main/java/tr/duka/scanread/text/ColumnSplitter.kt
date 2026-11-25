package tr.duka.scanread.text

import tr.duka.scanread.text.model.Box
import tr.duka.scanread.text.model.TextLine
import tr.duka.scanread.text.model.TextWord
import kotlin.math.max
import kotlin.math.min

/**
 * Separates side-by-side text columns that ended up in one block, e.g. overlapping balloons whose
 * interiors form a single light area and whose lines OCR read across both balloons.
 *
 * Words are linked when they are neighbours on a line (small gap) or stacked on adjacent lines
 * (overlapping horizontally); each connected set of words is a candidate column. Text within one
 * balloon stays connected through the lines above and below even where a single gap is wide.
 *
 * Widely spaced lettering can leave words of one balloon unlinked, yet they still sit beside each
 * other at the same height with ordinary word spacing. Lines of separate balloons rarely do, so
 * candidates that sit beside another one on most of their lines are merged again.
 */
class ColumnSplitter(
    /** Largest gap between neighbouring words of one phrase, relative to the word height. */
    private val maxWordGapRatio: Float = 0.45f,
    /** Largest gap between words that OCR may still read as one phrase, relative to the word height. */
    private val maxPhraseGapRatio: Float = 0.8f,
    /** Words on adjacent lines are linked when they overlap by this share of the narrower word. */
    private val minOverlapRatio: Float = 0.3f,
    /** Largest vertical gap between adjacent lines, relative to their average height. */
    private val maxLineGapRatio: Float = 0.9f,
    /** Blocks with fewer lines are left alone: without stacked lines, wide gaps are ambiguous. */
    private val minLines: Int = 3,
) {
    private class PlacedWord(val word: TextWord, val line: Int)

    fun split(lines: List<TextLine>): List<List<TextLine>> {
        if (lines.size < minLines) return listOf(lines)

        val words = lines.flatMapIndexed { index, line ->
            line.words.ifEmpty { listOf(TextWord(line.text, line.box)) }.map { PlacedWord(it, index) }
        }
        val parent = IntArray(words.size) { it }
        fun find(index: Int): Int {
            var i = index
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }

        for (i in words.indices) {
            for (j in i + 1 until words.size) {
                if (linked(words[i], words[j])) union(i, j)
            }
        }

        val components = mergeInterleaved(words.indices.groupBy(::find).values.map { it.map(words::get) })
        if (components.size == 1) return listOf(lines)

        return components
            .map { placed ->
                placed
                    .groupBy { it.line }
                    .map { (_, lineWords) -> lineOf(lineWords.map { it.word }.sortedBy { it.box.left }) }
                    .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            }
            .sortedWith(compareBy({ it.first().box.top }, { it.first().box.left }))
    }

    /**
     * Merges candidate columns that sit side by side on most of their lines with ordinary word
     * spacing: those are one widely spaced balloon, not two balloons OCR read across.
     */
    private fun mergeInterleaved(components: List<List<PlacedWord>>): List<List<PlacedWord>> {
        // Lines of each candidate that sit beside a word of another candidate, per candidate pair.
        val sideBySide = HashMap<Pair<Int, Int>, Pair<MutableSet<Int>, MutableSet<Int>>>()
        for (a in components.indices) {
            for (b in a + 1 until components.size) {
                for (wordA in components[a]) {
                    for (wordB in components[b]) {
                        if (!besideEachOther(wordA.word.box, wordB.word.box)) continue
                        val lines = sideBySide.getOrPut(a to b) { mutableSetOf<Int>() to mutableSetOf() }
                        lines.first += wordA.line
                        lines.second += wordB.line
                    }
                }
            }
        }

        val parent = IntArray(components.size) { it }
        fun find(index: Int): Int = if (parent[index] == index) index else find(parent[index])
        val lineCounts = components.map { words -> words.map { it.line }.toSet().size }
        sideBySide.forEach { (pair, lines) ->
            val mostlyBeside = lines.first.size * 2 > lineCounts[pair.first] ||
                lines.second.size * 2 > lineCounts[pair.second]
            if (mostlyBeside) parent[find(pair.first)] = find(pair.second)
        }
        return components.indices.groupBy(::find).values.map { indices -> indices.flatMap(components::get) }
    }

    /** Words at the same height with no more than ordinary word spacing between them. */
    private fun besideEachOther(a: Box, b: Box): Boolean {
        val height = max(a.height, b.height)
        val sameRow = -a.verticalGap(b) >= min(a.height, b.height) * MIN_ROW_OVERLAP_RATIO
        val gap = max(a.left, b.left) - min(a.right, b.right)
        return sameRow && gap <= height * maxPhraseGapRatio
    }

    private fun linked(a: PlacedWord, b: PlacedWord): Boolean {
        val boxA = a.word.box
        val boxB = b.word.box
        val height = max(boxA.height, boxB.height)
        if (height <= 0) return false

        if (a.line == b.line) {
            val gap = max(boxA.left, boxB.left) - min(boxA.right, boxB.right)
            return gap <= height * maxWordGapRatio
        }
        val averageHeight = (boxA.height + boxB.height) / 2f
        if (boxA.verticalGap(boxB) > averageHeight * maxLineGapRatio) return false
        val narrower = min(boxA.width, boxB.width)
        return narrower > 0 && boxA.horizontalOverlap(boxB) >= narrower * minOverlapRatio
    }

    private fun lineOf(words: List<TextWord>) = TextLine(
        text = words.joinToString(" ") { it.text },
        box = words.map(TextWord::box).reduce { acc, box -> acc.union(box) },
        words = words,
    )

    private companion object {
        /** Words share a row when they overlap vertically by this share of the shorter one. */
        const val MIN_ROW_OVERLAP_RATIO = 0.5f
    }
}
