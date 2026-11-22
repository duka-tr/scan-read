package com.scanread.capture

import com.scanread.text.model.Box
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenChangeDetectorTest {

    private val width = 360
    private val height = 800
    private val detector = ScreenChangeDetector(width, height, columns = 36, rows = 80)

    /** A "page": dark horizontal stripes every 40 px, shifted down by [scroll] px. */
    private fun page(scroll: Int = 0) = detector.grid { _, y -> if (((y + scroll) / 20) % 2 == 0) 30 else 230 }

    @Test
    fun `an identical screen is unchanged`() {
        assertFalse(detector.hasChanged(page(), page(), ignored = emptyList()))
    }

    @Test
    fun `scrolling the page counts as a change`() {
        assertTrue(detector.hasChanged(page(), page(scroll = 20), ignored = emptyList()))
    }

    @Test
    fun `switching to a different screen counts as a change`() {
        val homeScreen = detector.grid { _, _ -> 120 }
        assertTrue(detector.hasChanged(page(), homeScreen, ignored = emptyList()))
    }

    @Test
    fun `changes inside ignored areas, such as the overlay's own drawings, do not count`() {
        // The same page, with a white overlay drawn over the middle.
        val withOverlay = detector.grid { x, y ->
            when {
                x in 40 until 320 && y in 100 until 700 -> 255
                (y / 20) % 2 == 0 -> 30
                else -> 230
            }
        }

        assertTrue(detector.hasChanged(page(), withOverlay, ignored = emptyList()))
        assertFalse(detector.hasChanged(page(), withOverlay, ignored = listOf(Box(40, 100, 320, 700))))
    }
}
