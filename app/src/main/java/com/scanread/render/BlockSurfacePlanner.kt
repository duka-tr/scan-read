package com.scanread.render

import com.scanread.image.EnclosedRegionMap
import com.scanread.text.model.Box
import com.scanread.text.model.TextBlock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decides how each block is covered and where its translation goes.
 *
 * A block that alone occupies an enclosed area (a speech balloon) gets the whole balloon: the
 * interior is repainted with the balloon's own color (which also hides text OCR missed) and the
 * translation may use most of the balloon instead of only the original text's box. Other blocks
 * get a rounded rectangle in the color around the text.
 */
class BlockSurfacePlanner(
    private val regions: EnclosedRegionMap,
    private val pixels: IntArray,
    private val width: Int,
    private val height: Int,
    private val config: Config = Config(),
) {
    data class Config(
        /** Share of a balloon's bounding box usable for text; an ellipse holds a ~71% rectangle. */
        val balloonTextAreaRatio: Float = 0.76f,
        /** A shape much larger than its text (e.g. a whole white panel) is not treated as a balloon. */
        val maxShapeToTextAreaRatio: Float = 30f,
        /**
         * Text sits near the middle of a balloon. A larger offset (relative to the shape size) means
         * the area is not a closed balloon, e.g. an open balloon leaking into the panel background.
         */
        val maxTextOffsetRatio: Float = 0.22f,
        /** Above this share of enclosed dark content the area holds artwork, not just text. */
        val maxHoleRatio: Float = 0.4f,
        /** Padding (px) of rectangular covers around the original text. */
        val boxPadding: Int = 4,
        /**
         * Rectangular covers may grow by up to these factors so longer translations still fit.
         * Growth only proceeds into background-colored pixels, so it never covers outlines or art.
         */
        val boxGrowthWidth: Float = 1.8f,
        val boxGrowthHeight: Float = 1.4f,
        /** Width (px) of the ring around the text sampled for the cover color. */
        val colorSampleRing: Int = 3,
    )

    fun plan(blocks: List<TextBlock>): List<BlockSurface> {
        val blockRegions = blocks.map(::dominantEnclosedRegion)
        val blocksPerRegion = blockRegions.filterNotNull().groupingBy { it }.eachCount()
        return blocks.mapIndexed { index, block ->
            val region = blockRegions[index]
            val balloon = if (region != null && blocksPerRegion[region] == 1) balloonSurface(block, region) else null
            balloon ?: boxSurface(block)
        }
    }

    /** The enclosed area most lines of [block] sit in, if at least half of them do. */
    private fun dominantEnclosedRegion(block: TextBlock): Int? {
        val ids = block.lines.mapNotNull { regions.regionOf(it.box)?.takeIf { r -> r.isEnclosed }?.id }
        val (id, count) = ids.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return id.takeIf { count * 2 >= block.lines.size }
    }

    private fun balloonSurface(block: TextBlock, region: Int): BlockSurface? {
        val shape = regions.shapeOf(region)
        val textArea = block.box.width.toLong() * block.box.height
        if (textArea <= 0 || shape.area > textArea * config.maxShapeToTextAreaRatio) return null
        if (shape.holeRatio > config.maxHoleRatio) return null

        val bounds = shape.bounds
        val offsetX = abs(block.box.centerX - bounds.centerX).toFloat() / bounds.width
        val offsetY = abs(block.box.centerY - bounds.centerY).toFloat() / bounds.height
        if (offsetX > config.maxTextOffsetRatio || offsetY > config.maxTextOffsetRatio) return null

        val usableWidth = (bounds.width * config.balloonTextAreaRatio).toInt()
        val usableHeight = (bounds.height * config.balloonTextAreaRatio).toInt()
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2
        val inscribed = Box(
            left = centerX - usableWidth / 2,
            top = centerY - usableHeight / 2,
            right = centerX + usableWidth / 2,
            bottom = centerY + usableHeight / 2,
        )
        // Letters touching the outline are not enclosed by the light area; cover the OCR lines too.
        val mask = shape.mask.copyOf()
        for (line in block.lines) {
            val cover = line.box.expand(LINE_COVER_MARGIN, LINE_COVER_MARGIN).intersect(bounds)
            for (y in cover.top until cover.bottom) {
                for (x in cover.left until cover.right) mask[(y - bounds.top) * bounds.width + (x - bounds.left)] = true
            }
        }
        return BlockSurface(
            textArea = inscribed.union(block.box).intersect(bounds),
            fillBounds = bounds,
            fillMask = mask,
            fillColor = shape.color,
            textColor = contrastingTextColor(shape.color),
        )
    }

    private fun boxSurface(block: TextBlock): BlockSurface {
        val original = block.box.expand(config.boxPadding, config.boxPadding).intersect(Box(0, 0, width, height))
        val color = dominantRingColor(original)
        val box = growIntoBackground(block.box, color)
        val fill = box.expand(config.boxPadding, config.boxPadding).intersect(Box(0, 0, width, height))
        return BlockSurface(
            textArea = box,
            fillBounds = fill,
            fillMask = null,
            fillColor = color,
            textColor = contrastingTextColor(color),
        )
    }

    /**
     * Grows [box] (up to the configured factors) one pixel column/row at a time, but only where the
     * new strip is background colored, so covers never spill over outlines or artwork.
     */
    private fun growIntoBackground(box: Box, background: Int): Box {
        val maxExtraX = ((box.width * (config.boxGrowthWidth - 1f)) / 2).toInt()
        val maxExtraY = ((box.height * (config.boxGrowthHeight - 1f)) / 2).toInt()
        val padding = config.boxPadding
        var left = box.left
        var right = box.right
        var top = box.top
        var bottom = box.bottom

        fun columnIsBackground(x: Int) = x in 0 until width &&
            (max(0, top - padding) until min(height, bottom + padding)).all { y -> isBackground(pixels[y * width + x], background) }
        fun rowIsBackground(y: Int) = y in 0 until height &&
            (max(0, left - padding) until min(width, right + padding)).all { x -> isBackground(pixels[y * width + x], background) }

        repeat(maxExtraX) {
            if (columnIsBackground(left - padding - 1)) left--
            if (columnIsBackground(right + padding)) right++
        }
        repeat(maxExtraY) {
            if (rowIsBackground(top - padding - 1)) top--
            if (rowIsBackground(bottom + padding)) bottom++
        }
        return Box(left, top, right, bottom)
    }

    /**
     * Most common (quantized) color in a thin ring just outside [box]: the background around the
     * text. Unlike an average, nearby outlines do not tint it gray.
     */
    private fun dominantRingColor(box: Box): Int {
        val ring = config.colorSampleRing
        // Per quantized color: pixel count and channel sums, to return the bucket's true average.
        val buckets = HashMap<Int, LongArray>()
        for (y in max(0, box.top - ring) until min(height, box.bottom + ring)) {
            for (x in max(0, box.left - ring) until min(width, box.right + ring)) {
                if (box.contains(x, y)) continue
                val argb = pixels[y * width + x]
                val bucket = buckets.getOrPut(quantize(argb)) { LongArray(4) }
                bucket[0]++
                bucket[1] += (argb shr 16 and 0xFF).toLong()
                bucket[2] += (argb shr 8 and 0xFF).toLong()
                bucket[3] += (argb and 0xFF).toLong()
            }
        }
        val (count, red, green, blue) = buckets.values.maxByOrNull { it[0] } ?: return WHITE
        return (0xFF shl 24) or ((red / count).toInt() shl 16) or ((green / count).toInt() shl 8) or (blue / count).toInt()
    }

    private fun quantize(argb: Int): Int =
        ((argb shr 16 and 0xFF) / QUANT shl 8) or ((argb shr 8 and 0xFF) / QUANT shl 4) or ((argb and 0xFF) / QUANT)

    private fun isBackground(argb: Int, background: Int): Boolean =
        abs(luminance(argb) - luminance(background)) <= BACKGROUND_TOLERANCE

    companion object {
        const val WHITE = -0x1
        const val NEAR_BLACK = -0xeeeeef // #FF111111

        /** Dark text on light backgrounds, white text on dark ones. */
        fun contrastingTextColor(background: Int): Int =
            if (luminance(background) >= LIGHT_BACKGROUND_LUMINANCE) NEAR_BLACK else WHITE

        private fun luminance(argb: Int): Int {
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }

        private const val LIGHT_BACKGROUND_LUMINANCE = 128
        private const val LINE_COVER_MARGIN = 2
        private const val QUANT = 16
        private const val BACKGROUND_TOLERANCE = 40
    }
}
