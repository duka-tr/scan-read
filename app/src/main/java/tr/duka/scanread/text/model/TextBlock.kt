package tr.duka.scanread.text.model

/**
 * A group of lines that belong together (e.g. a caption or a speech balloon).
 *
 * @property sourceText lines joined exactly as recognized.
 * @property text normalized text, ready for translation.
 * @property isAllCaps the source was written in capitals; translations should be rendered the same way.
 */
data class TextBlock(
    val lines: List<TextLine>,
    val box: Box,
    val sourceText: String,
    val text: String,
    val isAllCaps: Boolean,
) {
    /**
     * Distance between line starts (px), used to size the translation. Capped relative to the median
     * line, so a logo or watermark that OCR merged into one line does not inflate the whole block.
     */
    val linePitch: Float
        get() {
            val pitch = box.height.toFloat() / lines.size
            val heights = lines.map { it.box.height }.sorted()
            val median = heights.getOrNull(heights.size / 2)?.takeIf { it > 0 } ?: return pitch
            return minOf(pitch, median * MAX_PITCH_TO_LINE_RATIO)
        }

    private companion object {
        const val MAX_PITCH_TO_LINE_RATIO = 1.6f
    }
}
