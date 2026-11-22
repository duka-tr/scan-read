package com.scanread.text

import com.scanread.text.model.Box
import com.scanread.text.model.TextLine
import com.scanread.text.model.TextWord
import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnSplitterTest {

    private val splitter = ColumnSplitter()

    /** A line from (text, left) pairs; words are 10 px per letter, 20 px tall. */
    private fun line(top: Int, vararg words: Pair<String, Int>): TextLine {
        val placed = words.map { (text, left) -> TextWord(text, Box(left, top, left + text.length * 10, top + 20)) }
        return TextLine(
            text = placed.joinToString(" ") { it.text },
            box = placed.map { it.box }.reduce { acc, box -> acc.union(box) },
            words = placed,
        )
    }

    @Test
    fun `separates staggered columns read as shared lines`() {
        val lines = listOf(
            line(100, "GOOD" to 100, "SEE" to 230),
            line(124, "MORNING" to 85, "YOU" to 230),
            line(148, "TO" to 110, "ALL" to 138, "SOON" to 225),
        )

        val columns = splitter.split(lines)

        assertEquals(
            listOf(listOf("GOOD", "MORNING", "TO ALL"), listOf("SEE", "YOU", "SOON")),
            columns.map { column -> column.map { it.text } },
        )
    }

    @Test
    fun `keeps a centered balloon with one wide gap together`() {
        val lines = listOf(
            line(100, "WHERE" to 100, "ARE" to 158),
            line(124, "YOU" to 95, "GOING" to 140),
            line(148, "SO" to 100, "LATE?" to 135),
        )

        assertEquals(1, splitter.split(lines).size)
    }

    @Test
    fun `leaves short blocks alone`() {
        val lines = listOf(line(100, "HEY" to 100, "THERE" to 200))

        assertEquals(1, splitter.split(lines).size)
    }
}
