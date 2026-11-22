package com.scanread.image

import com.scanread.text.RegionLookup
import com.scanread.text.model.Box

/**
 * Labels the connected light areas of an image and answers which area a piece of text sits in.
 * The inside of a speech balloon is such an area: light, and surrounded by a darker outline.
 *
 * Before labeling, dark pixels are grown by [Config.closingRadius] so that tiny breaks in outlines
 * (common after scaling and anti-aliasing) do not connect neighbouring balloons.
 *
 * An area is *enclosed* when it does not touch the image border and is not very large; the page
 * background and open layouts are therefore not treated as balloons.
 *
 * @param pixels ARGB pixels, row by row.
 */
class EnclosedRegionMap(
    private val pixels: IntArray,
    private val width: Int,
    private val height: Int,
    private val config: Config = Config(),
) : RegionLookup {

    data class Config(
        /** Minimum luminance (0–255) of a light pixel. Anti-aliased outline pixels stay below it. */
        val lightThreshold: Int = 180,
        /** Dark pixels are grown by this many pixels to seal small gaps in outlines. */
        val closingRadius: Int = 1,
        /** Largest share of the image an enclosed area may cover. */
        val maxAreaFraction: Float = 0.12f,
        /** How far (in pixels) a text box may stick out of the area it belongs to. */
        val enclosureTolerance: Int = 5,
        /** Extra margin (in pixels) around a text box that is searched for its area. */
        val searchMargin: Int = 4,
    )

    private class Area(val bounds: Box, val size: Int)

    private val labels = IntArray(width * height)
    private val areas = ArrayList<Area>()

    init {
        require(pixels.size == width * height) { "Pixel count does not match ${width}x$height" }
        labelAreas(shrinkLight(lightMask(pixels)))
    }

    override fun regionOf(box: Box): RegionLookup.Region? {
        val left = (box.left - config.searchMargin).coerceAtLeast(0)
        val top = (box.top - config.searchMargin).coerceAtLeast(0)
        val right = (box.right + config.searchMargin).coerceAtMost(width)
        val bottom = (box.bottom + config.searchMargin).coerceAtMost(height)
        if (left >= right || top >= bottom) return null

        // Count light pixels per area. Letter counters (inside "O", "A", …) are excluded below
        // because they cannot enclose the whole text box.
        val counts = HashMap<Int, Int>()
        for (y in top until bottom) {
            val row = y * width
            for (x in left until right) {
                val label = labels[row + x]
                if (label != 0) counts[label] = (counts[label] ?: 0) + 1
            }
        }

        val best = counts.entries
            .filter { encloses(areas[it.key - 1].bounds, box) }
            .maxByOrNull { it.value }
            ?.key
            ?: return null
        return RegionLookup.Region(id = best, isEnclosed = isEnclosed(areas[best - 1]))
    }

    /**
     * The filled shape of area [id]: the light area plus everything it surrounds (the text inside a
     * balloon), but not its outline. [RegionShape.color] is the average color of the light area.
     */
    fun shapeOf(id: Int): RegionShape {
        val bounds = areas[id - 1].bounds
        val shapeWidth = bounds.width
        val shapeHeight = bounds.height
        val size = shapeWidth * shapeHeight
        val inArea = BooleanArray(size) { i -> labels[(bounds.top + i / shapeWidth) * width + bounds.left + i % shapeWidth] == id }

        // Pixels outside the area that connect to the bounding box edge lie outside the shape;
        // the remaining ones are holes (text) inside it.
        val outside = BooleanArray(size)
        val queue = IntArray(size)
        var tail = 0
        fun seed(i: Int) {
            if (!inArea[i] && !outside[i]) { outside[i] = true; queue[tail++] = i }
        }
        for (x in 0 until shapeWidth) { seed(x); seed((shapeHeight - 1) * shapeWidth + x) }
        for (y in 0 until shapeHeight) { seed(y * shapeWidth); seed(y * shapeWidth + shapeWidth - 1) }
        var head = 0
        while (head < tail) {
            val p = queue[head++]
            val x = p % shapeWidth
            val y = p / shapeWidth
            if (x > 0) seed(p - 1)
            if (x < shapeWidth - 1) seed(p + 1)
            if (y > 0) seed(p - shapeWidth)
            if (y < shapeHeight - 1) seed(p + shapeWidth)
        }

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (i in 0 until size) {
            if (!inArea[i]) continue
            val argb = pixels[(bounds.top + i / shapeWidth) * width + bounds.left + i % shapeWidth]
            red += argb shr 16 and 0xFF
            green += argb shr 8 and 0xFF
            blue += argb and 0xFF
            count++
        }
        val color = if (count == 0L) {
            OPAQUE_WHITE
        } else {
            (0xFF shl 24) or ((red / count).toInt() shl 16) or ((green / count).toInt() shl 8) or (blue / count).toInt()
        }

        return RegionShape(bounds, BooleanArray(size) { inArea[it] || !outside[it] }, color, lightArea = count.toInt())
    }

    private fun encloses(bounds: Box, box: Box): Boolean {
        val tolerance = config.enclosureTolerance
        return bounds.left <= box.left + tolerance &&
            bounds.top <= box.top + tolerance &&
            bounds.right >= box.right - tolerance &&
            bounds.bottom >= box.bottom - tolerance
    }

    private fun isEnclosed(area: Area): Boolean {
        val bounds = area.bounds
        val touchesBorder = bounds.left <= 0 || bounds.top <= 0 || bounds.right >= width || bounds.bottom >= height
        return !touchesBorder && area.size <= width.toLong() * height * config.maxAreaFraction
    }

    private fun lightMask(pixels: IntArray): BooleanArray {
        val lightLimit = config.lightThreshold * LUMINANCE_SCALE
        return BooleanArray(pixels.size) { i ->
            val argb = pixels[i]
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            r * 299 + g * 587 + b * 114 >= lightLimit
        }
    }

    /**
     * Keeps a pixel light only if every pixel within [Config.closingRadius] is light, i.e. grows the
     * dark pixels. Done as two separable passes (rows, then columns). Pixels outside the image
     * count as light so areas can still reach the border.
     */
    private fun shrinkLight(light: BooleanArray): BooleanArray {
        val radius = config.closingRadius
        if (radius <= 0) return light

        val horizontal = BooleanArray(light.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var allLight = true
                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until width && !light[row + nx]) { allLight = false; break }
                }
                horizontal[row + x] = allLight
            }
        }

        val result = BooleanArray(light.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var allLight = true
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until height && !horizontal[ny * width + x]) { allLight = false; break }
                }
                result[y * width + x] = allLight
            }
        }
        return result
    }

    /** Labels 4-connected light areas with a breadth-first flood fill. */
    private fun labelAreas(light: BooleanArray) {
        val queue = IntArray(light.size)

        for (start in light.indices) {
            if (labels[start] != 0 || !light[start]) continue

            val label = areas.size + 1
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = label
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1

            while (head < tail) {
                val p = queue[head++]
                val x = p % width
                val y = p / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0 && labels[p - 1] == 0 && light[p - 1]) { labels[p - 1] = label; queue[tail++] = p - 1 }
                if (x < width - 1 && labels[p + 1] == 0 && light[p + 1]) { labels[p + 1] = label; queue[tail++] = p + 1 }
                if (y > 0 && labels[p - width] == 0 && light[p - width]) { labels[p - width] = label; queue[tail++] = p - width }
                if (y < height - 1 && labels[p + width] == 0 && light[p + width]) {
                    labels[p + width] = label
                    queue[tail++] = p + width
                }
            }
            areas += Area(Box(minX, minY, maxX + 1, maxY + 1), tail)
        }
    }

    private companion object {
        /** Luminance weights (299/587/114) sum to 1000. */
        const val LUMINANCE_SCALE = 1000
        const val OPAQUE_WHITE = -0x1
    }
}

/**
 * A filled image area in screen pixels.
 *
 * @property mask row-major, [bounds] sized; `true` for pixels inside the shape.
 * @property color average ARGB color of the area.
 * @property lightArea number of light pixels; the rest of [area] are enclosed dark pixels (holes).
 */
class RegionShape(val bounds: Box, val mask: BooleanArray, val color: Int, val lightArea: Int) {
    val area: Int = mask.count { it }

    /** Share of the shape covered by enclosed dark content: text in a balloon, artwork in a panel. */
    val holeRatio: Float get() = if (area == 0) 0f else 1f - lightArea.toFloat() / area
}
