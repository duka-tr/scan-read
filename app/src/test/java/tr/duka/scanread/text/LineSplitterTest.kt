package tr.duka.scanread.text

import tr.duka.scanread.text.model.Box
import tr.duka.scanread.text.model.TextLine
import tr.duka.scanread.text.model.TextWord
import org.junit.Assert.assertEquals
import org.junit.Test

class LineSplitterTest {

    private val splitter = LineSplitter()

    private fun word(text: String, left: Int, right: Int) = TextWord(text, Box(left, 100, right, 120))

    private fun lineOf(vararg words: TextWord) = TextLine(
        text = words.joinToString(" ") { it.text },
        box = words.map { it.box }.reduce { acc, box -> acc.union(box) },
        words = words.toList(),
    )

    @Test
    fun `keeps a line with normal word spacing intact`() {
        val line = lineOf(word("HAVE", 100, 150), word("A", 158, 170), word("GOOD", 178, 230))

        assertEquals(listOf(line), splitter.split(line))
    }

    @Test
    fun `splits a line that spans two areas`() {
        val line = lineOf(word("MORNING", 100, 190), word("WE'LL", 250, 320), word("SEE", 328, 380))

        val parts = splitter.split(line)

        assertEquals(listOf("MORNING", "WE'LL SEE"), parts.map { it.text })
        assertEquals(Box(250, 100, 380, 120), parts[1].box)
    }

    @Test
    fun `splits words that sit in different regions`() {
        val line = lineOf(word("MORNING", 100, 190), word("WE'LL", 206, 280))
        val regions = RegionLookup { box -> RegionLookup.Region(id = if (box.left < 200) 1 else 2, isEnclosed = true) }

        assertEquals(listOf("MORNING", "WE'LL"), splitter.split(line, regions).map { it.text })
    }

    @Test
    fun `keeps lines without word positions intact`() {
        val line = TextLine("AND TO", Box(0, 0, 300, 20))

        assertEquals(listOf(line), splitter.split(line))
    }
}
