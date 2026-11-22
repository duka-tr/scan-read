package tr.duka.scanread.overlay

import android.annotation.SuppressLint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.Locale
import kotlin.math.max

/**
 * Finds the largest text size at which [text] fits a given area and returns the laid-out text
 * (centered, balanced line breaks).
 *
 * Words are kept whole when possible. If even the minimum size does not fit, words may be split
 * at proper hyphenation points of [locale]; as a last resort the text overflows the area.
 */
class TextFitter(
    private val basePaint: TextPaint,
    private val minTextSize: Float,
    private val locale: Locale = Locale.getDefault(),
) {
    /**
     * @property fits `false` when even the minimum size does not fit; the layout may then
     * overflow the area.
     */
    class Result(val layout: StaticLayout, val fits: Boolean)

    fun fit(text: String, width: Int, height: Int, maxTextSize: Float): Result {
        val layoutWidth = width.coerceAtLeast(1)
        val upper = max(maxTextSize, minTextSize)

        search(text, layoutWidth, height, upper, hyphenate = false)?.let { return Result(it, fits = true) }
        search(text, layoutWidth, height, upper, hyphenate = true)?.let { return Result(it, fits = true) }

        // Overflowing a little is more readable than words broken in the middle: widen the
        // layout to the longest word if needed (the caller centers it on the area).
        val paint = paint(minTextSize)
        val longestWord = text.split(' ').maxOf { paint.measureText(it) }.toInt() + 1
        return Result(build(text, max(layoutWidth, longestWord), minTextSize, hyphenate = false), fits = false)
    }

    /** Largest size in [minTextSize]..[upper] whose layout fits, or `null` if none does. */
    private fun search(text: String, width: Int, height: Int, upper: Float, hyphenate: Boolean): StaticLayout? {
        val largest = build(text, width, upper, hyphenate)
        if (fits(largest, text, width, height, hyphenate)) return largest
        val smallest = build(text, width, minTextSize, hyphenate)
        if (!fits(smallest, text, width, height, hyphenate)) return null

        var low = minTextSize
        var high = upper
        var best = smallest
        repeat(SEARCH_STEPS) {
            val mid = (low + high) / 2
            val layout = build(text, width, mid, hyphenate)
            if (fits(layout, text, width, height, hyphenate)) {
                best = layout
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    private fun paint(textSize: Float) = TextPaint(basePaint).apply {
        this.textSize = textSize
        textLocale = locale
    }

    // LineBreaker constants (API 29) have the same values as the Layout constants used here.
    @SuppressLint("WrongConstant")
    private fun build(text: String, width: Int, textSize: Float, hyphenate: Boolean): StaticLayout =
        // Each layout keeps its own paint: StaticLayout draws with the paint it was built with.
        StaticLayout.Builder.obtain(text, 0, text.length, paint(textSize), width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(
                if (hyphenate) Layout.HYPHENATION_FREQUENCY_FULL else Layout.HYPHENATION_FREQUENCY_NONE,
            )
            .build()

    private fun fits(layout: StaticLayout, text: String, width: Int, height: Int, hyphenate: Boolean): Boolean {
        if (layout.height > height) return false
        if (!hyphenate) {
            val paint = layout.paint
            return text.split(' ').all { paint.measureText(it) <= width }
        }
        // With hyphenation, a line may end inside a word only at a hyphenation point.
        for (line in 0 until layout.lineCount) {
            if (layout.getLineWidth(line) > width + WIDTH_TOLERANCE) return false
            val end = layout.getLineEnd(line)
            val breaksWord = end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()
            if (breaksWord && !endsWithHyphen(layout, text, line)) return false
        }
        return true
    }

    /**
     * Whether the layout drew a hyphen at the end of [line]: the line is then wider than its
     * characters by about one hyphen (the hyphen query APIs are not public).
     */
    private fun endsWithHyphen(layout: StaticLayout, text: String, line: Int): Boolean {
        val paint = layout.paint
        val charactersWidth = paint.measureText(text, layout.getLineStart(line), layout.getLineEnd(line))
        return layout.getLineWidth(line) - charactersWidth >= paint.measureText(HYPHEN) * HYPHEN_WIDTH_SHARE
    }

    private companion object {
        const val SEARCH_STEPS = 8
        const val LINE_SPACING = 0.95f
        const val WIDTH_TOLERANCE = 0.5f
        const val HYPHEN = "-"
        const val HYPHEN_WIDTH_SHARE = 0.5f
    }
}
