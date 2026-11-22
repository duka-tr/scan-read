package com.scanread.text

import com.scanread.text.model.TextLine
import com.scanread.text.model.TextWord
import kotlin.math.max
import kotlin.math.min

/**
 * Separates side-by-side text columns that ended up in one block, e.g. overlapping balloons whose
 * interiors form a single light area and whose lines OCR read across both balloons.
 *
 * Words are linked when they are neighbours on a line (small gap) or stacked on adjacent lines
 * (overlapping horizontally); each connected set of words becomes its own block. Text within one
 * balloon stays connected through the lines above and below even where a single gap is wide.
 */
class ColumnSplitter(
    /** Largest gap between neighbouring words of one phrase, relative to the word height. */
    private val maxWordGapRatio: Float = 0.45f,
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

        val components = words.indices.groupBy(::find).values
        if (components.size == 1) return listOf(lines)

        return components
            .map { indices ->
                indices.map(words::get)
                    .groupBy { it.line }
                    .map { (_, lineWords) -> lineOf(lineWords.map { it.word }.sortedBy { it.box.left }) }
                    .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            }
            .sortedWith(compareBy({ it.first().box.top }, { it.first().box.left }))
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
