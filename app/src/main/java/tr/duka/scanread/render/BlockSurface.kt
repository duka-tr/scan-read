package tr.duka.scanread.render

import tr.duka.scanread.text.model.Box

/**
 * Where and how a translated block is drawn.
 *
 * @property textArea area the translation is fitted into.
 * @property fillBounds area covered to hide the original text.
 * @property fillMask optional row-major mask ([fillBounds] sized) for shaped fills such as a balloon
 * interior; `null` means a rounded rectangle over [fillBounds].
 * @property fillColor ARGB color of the cover, taken from the surrounding image.
 * @property textColor ARGB color for the translation, contrasting with [fillColor].
 */
class BlockSurface(
    val textArea: Box,
    val fillBounds: Box,
    val fillMask: BooleanArray?,
    val fillColor: Int,
    val textColor: Int,
) {
    val isShaped: Boolean get() = fillMask != null
}
