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
)
