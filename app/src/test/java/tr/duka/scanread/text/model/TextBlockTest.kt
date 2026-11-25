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
        val block = block(Box(0, 0, 100, 30), Box(0, 45, 100, 75), Box(0, 90, 100, 120))

        assertEquals(40f, block.linePitch, 0.01f)
    }

    @Test
    fun `a line inflated by an overlapping logo does not enlarge the pitch`() {
        val block = block(Box(0, 0, 100, 30), Box(0, 45, 100, 75), Box(0, 90, 160, 200))

        assertEquals(48f, block.linePitch, 0.01f)
    }
}
