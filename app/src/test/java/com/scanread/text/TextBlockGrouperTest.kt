package com.scanread.text

import com.scanread.text.model.Box
import com.scanread.text.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Test

class TextBlockGrouperTest {

    private val grouper = TextBlockGrouper()

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        TextLine(text, Box(left, top, right, bottom))

    @Test
    fun `centered lines of one balloon form a single block`() {
        val lines = listOf(
            line("WHERE DO YOU", 120, 100, 280, 130),
            line("THINK YOU ARE", 110, 136, 290, 166),
            line("GOING?", 160, 172, 240, 202),
        )

        val groups = grouper.group(lines)

        assertEquals(1, groups.size)
        assertEquals(listOf("WHERE DO YOU", "THINK YOU ARE", "GOING?"), groups[0].map { it.text })
    }

    @Test
    fun `side by side balloons stay separate`() {
        val lines = listOf(
            line("HELLO", 100, 100, 200, 130),
            line("THERE", 100, 136, 200, 166),
            line("GO AWAY", 400, 100, 520, 130),
            line("NOW", 430, 136, 490, 166),
        )

        val groups = grouper.group(lines)

        assertEquals(2, groups.size)
        assertEquals(listOf("HELLO", "THERE"), groups[0].map { it.text })
        assertEquals(listOf("GO AWAY", "NOW"), groups[1].map { it.text })
    }

    @Test
    fun `distant balloons in the same column stay separate`() {
        val lines = listOf(
            line("FIRST", 100, 100, 200, 130),
            line("SECOND", 100, 400, 210, 430),
        )

        assertEquals(2, grouper.group(lines).size)
    }

    @Test
    fun `lines with very different heights stay separate`() {
        val lines = listOf(
            line("BOOM", 100, 100, 300, 180),
            line("small caption", 110, 185, 290, 205),
        )

        assertEquals(2, grouper.group(lines).size)
    }

    @Test
    fun `an emphasized larger word stays in its balloon`() {
        val lines = listOf(
            line("THIS IS", 420, 925, 540, 945),
            line("MY", 455, 948, 500, 985),
            line("HOUSE!", 440, 985, 520, 1003),
        )

        assertEquals(1, grouper.group(lines).size)
    }

    @Test
    fun `lines in different regions are never merged`() {
        val top = line("HELLO", 100, 100, 200, 130)
        val bottom = line("THERE", 100, 136, 200, 166)
        val regions = RegionLookup { box ->
            RegionLookup.Region(id = if (box.top < 133) 1 else 2, isEnclosed = true)
        }

        assertEquals(2, grouper.group(listOf(top, bottom), regions).size)
    }

    @Test
    fun `lines in the same enclosed region merge despite large size differences`() {
        val lines = listOf(
            line("YOU THINK", 100, 100, 220, 120),
            line("WHAT", 90, 135, 230, 200),
        )
        val sameBalloon = RegionLookup { RegionLookup.Region(id = 7, isEnclosed = true) }

        assertEquals(2, grouper.group(lines).size)
        assertEquals(1, grouper.group(lines, sameBalloon).size)
    }

    @Test
    fun `groups are returned in reading order`() {
        val lines = listOf(
            line("BOTTOM", 100, 800, 200, 830),
            line("TOP RIGHT", 500, 100, 620, 130),
            line("TOP LEFT", 100, 100, 210, 130),
        )

        val order = grouper.group(lines).map { it.first().text }

        assertEquals(listOf("TOP LEFT", "TOP RIGHT", "BOTTOM"), order)
    }

    @Test
    fun `empty input gives no groups`() {
        assertEquals(emptyList<List<TextLine>>(), grouper.group(emptyList()))
    }
}
