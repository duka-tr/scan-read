package com.scanread.capture

import com.scanread.text.model.Box
import kotlin.math.abs

/**
 * Detects that the screen under the translation overlay changed (another app, scrolling), by
 * comparing coarse luminance grids of the screen against the one captured for translation.
 *
 * Areas the app draws itself (covers, translated text, the floating button) and system bars are
 * ignored, because they legitimately differ from the original capture.
 */
class ScreenChangeDetector(
    private val screenWidth: Int,
    private val screenHeight: Int,
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    /** A cell counts as changed when its average luminance moved by more than this (0–255). */
    private val cellThreshold: Int = DEFAULT_CELL_THRESHOLD,
    /** The screen counts as changed when this share of the compared cells changed. */
    private val changedShareThreshold: Float = DEFAULT_CHANGED_SHARE,
) {
    /**
     * Builds a luminance grid by averaging a few samples per cell.
     *
     * @param luminanceAt luminance (0–255) of the screen pixel at (x, y).
     */
    fun grid(luminanceAt: (x: Int, y: Int) -> Int): IntArray = IntArray(columns * rows) { cell ->
        val column = cell % columns
        val row = cell / columns
        var sum = 0
        for (sy in 0 until SAMPLES_PER_AXIS) {
            for (sx in 0 until SAMPLES_PER_AXIS) {
                val x = ((column + (sx + 0.5f) / SAMPLES_PER_AXIS) * screenWidth / columns).toInt()
                val y = ((row + (sy + 0.5f) / SAMPLES_PER_AXIS) * screenHeight / rows).toInt()
                sum += luminanceAt(x.coerceIn(0, screenWidth - 1), y.coerceIn(0, screenHeight - 1))
            }
        }
        sum / (SAMPLES_PER_AXIS * SAMPLES_PER_AXIS)
    }

    /** Share (0..1) of compared cells whose luminance changed between [baseline] and [current]. */
    fun changedShare(baseline: IntArray, current: IntArray, ignored: List<Box>): Float {
        val skip = BooleanArray(columns * rows)
        for (box in ignored) {
            // Cells touching an ignored box are skipped entirely (anti-aliased edges spill over).
            val left = (box.left * columns / screenWidth - 1).coerceAtLeast(0)
            val right = ((box.right - 1) * columns / screenWidth + 1).coerceAtMost(columns - 1)
            val top = (box.top * rows / screenHeight - 1).coerceAtLeast(0)
            val bottom = ((box.bottom - 1) * rows / screenHeight + 1).coerceAtMost(rows - 1)
            for (row in top..bottom) for (column in left..right) skip[row * columns + column] = true
        }
        var compared = 0
        var changed = 0
        for (cell in baseline.indices) {
            if (skip[cell]) continue
            compared++
            if (abs(baseline[cell] - current[cell]) > cellThreshold) changed++
        }
        return if (compared == 0) 0f else changed.toFloat() / compared
    }

    fun hasChanged(baseline: IntArray, current: IntArray, ignored: List<Box>): Boolean =
        changedShare(baseline, current, ignored) > changedShareThreshold

    companion object {
        const val DEFAULT_COLUMNS = 36
        const val DEFAULT_ROWS = 80
        private const val SAMPLES_PER_AXIS = 3
        private const val DEFAULT_CELL_THRESHOLD = 30
        private const val DEFAULT_CHANGED_SHARE = 0.12f

        fun luminance(argb: Int): Int {
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
    }
}
