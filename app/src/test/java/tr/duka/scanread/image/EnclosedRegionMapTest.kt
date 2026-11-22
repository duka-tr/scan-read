package tr.duka.scanread.image

import tr.duka.scanread.text.model.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnclosedRegionMapTest {

    // Large enough that the test balloons stay below the maximum enclosed-area share.
    private val width = 200
    private val height = 120

    private fun whiteImage() = IntArray(width * height) { WHITE }

    /** Draws a 2 px dark rectangle outline. */
    private fun IntArray.outline(left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top until bottom) {
            for (x in left until right) {
                val onEdge = x < left + 2 || x >= right - 2 || y < top + 2 || y >= bottom - 2
                if (onEdge) this[y * width + x] = BLACK
            }
        }
    }

    @Test
    fun `text in two touching balloons gets different enclosed regions`() {
        val pixels = whiteImage().apply {
            outline(5, 5, 40, 35)
            outline(38, 5, 75, 35)
        }
        val map = EnclosedRegionMap(pixels, width, height)

        val left = map.regionOf(Box(12, 15, 30, 22))
        val right = map.regionOf(Box(46, 15, 66, 22))

        assertNotNull(left)
        assertNotNull(right)
        assertTrue(left!!.isEnclosed)
        assertTrue(right!!.isEnclosed)
        assertNotEquals(left.id, right.id)
    }

    @Test
    fun `a one pixel break in the outline is sealed`() {
        val pixels = whiteImage().apply {
            outline(5, 5, 40, 35)
            // Break the right edge for one pixel row.
            this[20 * width + 38] = WHITE
            this[20 * width + 39] = WHITE
        }
        val map = EnclosedRegionMap(pixels, width, height)

        assertTrue(map.regionOf(Box(12, 15, 30, 22))!!.isEnclosed)
    }

    @Test
    fun `text on the page background is not enclosed`() {
        val pixels = whiteImage().apply { outline(5, 5, 40, 35) }
        val map = EnclosedRegionMap(pixels, width, height)

        val region = map.regionOf(Box(50, 15, 70, 22))

        assertNotNull(region)
        assertFalse(region!!.isEnclosed)
    }

    @Test
    fun `lines in the same balloon share a region`() {
        val pixels = whiteImage().apply { outline(5, 5, 60, 35) }
        val map = EnclosedRegionMap(pixels, width, height)

        assertEquals(map.regionOf(Box(12, 10, 40, 16))?.id, map.regionOf(Box(15, 22, 50, 28))?.id)
    }

    @Test
    fun `shape covers the balloon interior including its text but not the outline`() {
        val pixels = whiteImage().apply {
            outline(5, 5, 60, 35)
            // "Text": a dark block inside the balloon.
            for (y in 15 until 20) for (x in 20 until 40) this[y * width + x] = BLACK
        }
        val map = EnclosedRegionMap(pixels, width, height)
        val region = map.regionOf(Box(18, 13, 42, 22))!!

        val shape = map.shapeOf(region.id)
        fun inShape(x: Int, y: Int) = shape.bounds.contains(x, y) &&
            shape.mask[(y - shape.bounds.top) * shape.bounds.width + (x - shape.bounds.left)]

        assertTrue("text pixels are part of the shape", inShape(30, 17))
        assertTrue("interior is part of the shape", inShape(10, 10))
        assertFalse("outline is not part of the shape", inShape(5, 20))
        assertEquals(WHITE, shape.color)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
