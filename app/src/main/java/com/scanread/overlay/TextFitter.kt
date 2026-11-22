package com.scanread.overlay

import android.annotation.SuppressLint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

/**
 * Finds the largest text size at which [text] fits a given area without breaking words,
 * and returns the laid-out text (centered, balanced line breaks).
 */
class TextFitter(
    private val basePaint: TextPaint,
    private val minTextSize: Float,
) {
    /**
     * @property fits `false` when even the minimum size does not fit; the layout may then
     * overflow the area or break words.
     */
    class Result(val layout: StaticLayout, val fits: Boolean)

    fun fit(text: String, width: Int, height: Int, maxTextSize: Float): Result {
        val layoutWidth = width.coerceAtLeast(1)
        val upper = max(maxTextSize, minTextSize)

        build(text, layoutWidth, upper).let { if (fits(it, text, layoutWidth, height)) return Result(it, fits = true) }

        var low = minTextSize
        var high = upper
        val smallest = build(text, layoutWidth, minTextSize)
        if (!fits(smallest, text, layoutWidth, height)) {
            // Overflowing a little is more readable than words broken in the middle: widen the
            // layout to the longest word if needed (the caller centers it on the area).
            val paint = TextPaint(basePaint).apply { textSize = minTextSize }
            val longestWord = text.split(' ').maxOf { paint.measureText(it) }.toInt() + 1
            return Result(build(text, max(layoutWidth, longestWord), minTextSize), fits = false)
        }

        var best = smallest
        repeat(SEARCH_STEPS) {
            val mid = (low + high) / 2
            val layout = build(text, layoutWidth, mid)
            if (fits(layout, text, layoutWidth, height)) {
                best = layout
                low = mid
            } else {
                high = mid
            }
        }
        return Result(best, fits = true)
    }

    // LineBreaker.BREAK_STRATEGY_BALANCED (API 29) has the same value as the Layout constant used here.
    @SuppressLint("WrongConstant")
    private fun build(text: String, width: Int, textSize: Float): StaticLayout {
        // Each layout keeps its own paint: StaticLayout draws with the paint it was built with.
        val paint = TextPaint(basePaint).apply { this.textSize = textSize }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .build()
    }

    private fun fits(layout: StaticLayout, text: String, width: Int, height: Int): Boolean {
        if (layout.height > height) return false
        val paint = layout.paint
        return text.split(' ').all { paint.measureText(it) <= width }
    }

    private companion object {
        const val SEARCH_STEPS = 8
        const val LINE_SPACING = 0.95f
    }
}
