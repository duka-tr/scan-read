package com.scanread.render

import com.scanread.image.EnclosedRegionMap
import com.scanread.text.model.Box
import com.scanread.text.model.TextBlock
import com.scanread.text.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSurfacePlannerTest {

    private val width = 400
    private val height = 300

    private fun image(background: Int = WHITE) = IntArray(width * height) { background }

    private fun IntArray.outline(left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top until bottom) for (x in left until right) {
            if (x < left + 2 || x >= right - 2 || y < top + 2 || y >= bottom - 2) this[y * width + x] = BLACK
        }
    }

    private fun block(vararg boxes: Box) = TextBlock(
        lines = boxes.map { TextLine("TEXT", it) },
        box = boxes.reduce { acc, box -> acc.union(box) },
        sourceText = "TEXT",
        text = "Text",
        isAllCaps = true,
    )

    private fun planner(pixels: IntArray) =
        BlockSurfacePlanner(EnclosedRegionMap(pixels, width, height), pixels, width, height)

    @Test
    fun `a block alone in a balloon gets the balloon shape and a larger text area`() {
        val pixels = image().apply { outline(20, 20, 140, 100) }
        val textBlock = block(Box(60, 50, 100, 60), Box(62, 62, 98, 72))

        val surface = planner(pixels).plan(listOf(textBlock)).single()

        assertTrue(surface.isShaped)
        assertTrue("text area grows beyond the original text", surface.textArea.width > textBlock.box.width)
        assertEquals(WHITE, surface.fillColor)
        assertEquals(BlockSurfacePlanner.NEAR_BLACK, surface.textColor)
    }

    @Test
    fun `blocks sharing one area get rectangular covers`() {
        val pixels = image().apply { outline(20, 20, 300, 100) }
        val blocks = listOf(block(Box(40, 50, 90, 60)), block(Box(200, 50, 260, 60)))

        val surfaces = planner(pixels).plan(blocks)

        surfaces.forEach { assertFalse(it.isShaped) }
    }

    @Test
    fun `text on the page background gets a rectangle in the surrounding color`() {
        val dark = 0xFF202020.toInt()
        val textBlock = block(Box(150, 150, 200, 165))

        val surface = planner(image(background = dark)).plan(listOf(textBlock)).single()

        assertFalse(surface.isShaped)
        assertEquals(dark, surface.fillColor)
        assertEquals(BlockSurfacePlanner.WHITE, surface.textColor)
    }

    @Test
    fun `unrecognized letters right above a block are covered too`() {
        val pixels = image().apply {
            // A short stylized word OCR missed, on the line right above the recognized one.
            for (y in 135 until 155) for (x in 150 until 156) this[y * width + x] = BLACK
            for (y in 135 until 155) for (x in 162 until 172) this[y * width + x] = BLACK
            // The recognized line itself.
            for (y in 160 until 180) for (x in 130 until 210) this[y * width + x] = BLACK
        }
        val textBlock = block(Box(130, 160, 210, 180))

        val surface = planner(pixels).plan(listOf(textBlock)).single()

        assertTrue("cover reaches the missed word", surface.fillBounds.top <= 135)
    }

    @Test
    fun `artwork touching the search window is not absorbed`() {
        val pixels = image().apply {
            // A long vertical line (e.g. a panel border) left of the text.
            for (y in 0 until height) for (x in 118 until 121) this[y * width + x] = BLACK
            for (y in 160 until 180) for (x in 130 until 210) this[y * width + x] = BLACK
        }
        val textBlock = block(Box(130, 160, 210, 180))

        val surface = planner(pixels).plan(listOf(textBlock)).single()

        assertTrue("cover stays right of the border", surface.fillBounds.left >= 121)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
