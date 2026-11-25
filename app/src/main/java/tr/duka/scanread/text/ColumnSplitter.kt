package tr.duka.scanread.text

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
 * Widely spaced lettering can leave words of one balloon unlinked. OCR then still reads them as
 * shared lines with ordinary word spacing, whereas lines of separate balloons are mostly read on
 * their own, so candidates that sit side by side on most of their lines are merged again.
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
        val componentOf = HashMap<PlacedWord, Int>()
        components.forEachIndexed { index, words -> words.forEach { componentOf[it] = index } }
        val sharedLines = HashMap<Pair<Int, Int>, MutableSet<Int>>()
        components.flatten().groupBy { it.line }.forEach { (line, lineWords) ->
            lineWords.sortedBy { it.word.box.left }.zipWithNext { left, right ->
                val a = componentOf.getValue(left)
                val b = componentOf.getValue(right)
                val height = max(left.word.box.height, right.word.box.height)
                if (a != b && right.word.box.left - left.word.box.right <= height * maxPhraseGapRatio) {
                    sharedLines.getOrPut(min(a, b) to max(a, b)) { mutableSetOf() } += line
                }
            }
        }

        val parent = IntArray(components.size) { it }
        fun find(index: Int): Int = if (parent[index] == index) index else find(parent[index])
        val lineCounts = components.map { words -> words.map { it.line }.toSet().size }
        sharedLines.forEach { (pair, lines) ->
            if (lines.size * 2 > min(lineCounts[pair.first], lineCounts[pair.second])) {
                parent[find(pair.first)] = find(pair.second)
            }
        }
        return components.indices.groupBy(::find).values.map { indices -> indices.flatMap(components::get) }
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
}
