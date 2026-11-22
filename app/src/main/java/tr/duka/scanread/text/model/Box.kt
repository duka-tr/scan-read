package tr.duka.scanread.text.model

import kotlin.math.max
import kotlin.math.min

/** Axis-aligned rectangle in screen pixels. Platform-independent so text logic stays unit-testable. */
data class Box(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun union(other: Box): Box = Box(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )

    fun expand(horizontal: Int, vertical: Int): Box =
        Box(left - horizontal, top - vertical, right + horizontal, bottom + vertical)

    /** The overlapping part of both boxes; empty (zero size) when they do not overlap. */
    fun intersect(other: Box): Box {
        val left = max(left, other.left)
        val top = max(top, other.top)
        return Box(left, top, max(left, min(right, other.right)), max(top, min(bottom, other.bottom)))
    }

    /** Width of the shared horizontal range; zero or negative when the boxes do not overlap horizontally. */
    fun horizontalOverlap(other: Box): Int = min(right, other.right) - max(left, other.left)

    /** Empty space between the boxes vertically; negative when they overlap vertically. */
    fun verticalGap(other: Box): Int = max(top, other.top) - min(bottom, other.bottom)
}
