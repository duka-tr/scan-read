package tr.duka.scanread.text.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBlockTest {

    private fun block(vararg lines: Box): TextBlock = TextBlock(
        lines = lines.map { TextLine("WORD", it) },
        box = lines.reduce { acc, box -> acc.union(box) },
        sourceText = "",
        text = "",
        isAllCaps = true,
    )

    @Test
    fun `line pitch follows the line spacing`() {
        val block = block(Box(0, 0, 100, 30), Box(0, 35, 100, 65), Box(0, 70, 100, 100))

        assertEquals(33.33f, block.linePitch, 0.01f)
    }

    @Test
    fun `widely spaced lines do not enlarge the pitch beyond the letters`() {
        val block = block(Box(0, 0, 100, 30), Box(0, 47, 100, 77), Box(0, 94, 100, 124))

        assertEquals(37.5f, block.linePitch, 0.01f)
    }

    @Test
    fun `a line inflated by an overlapping logo does not enlarge the pitch`() {
        val block = block(Box(0, 0, 100, 30), Box(0, 45, 100, 75), Box(0, 90, 160, 200))

        assertEquals(37.5f, block.linePitch, 0.01f)
    }
}
