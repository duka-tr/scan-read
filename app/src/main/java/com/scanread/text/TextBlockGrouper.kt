package com.scanread.text

import com.scanread.text.model.TextLine
import kotlin.math.max
import kotlin.math.min

/**
 * Groups OCR lines into blocks.
 *
 * Lines in different image areas (see [RegionLookup]) are never merged; lines in the same enclosed
 * area (e.g. one speech balloon) are merged with relaxed layout rules. Otherwise, layout alone
 * decides: lines of similar height that are stacked closely and overlap horizontally belong together.
 */
class TextBlockGrouper(
    private val config: Config = Config(),
) {
    data class Config(
        /** Maximum vertical gap between two lines, relative to their average height. */
        val maxLineGapRatio: Float = 0.9f,
        /**
         * Minimum ratio between the shorter and the taller line height. Low enough to keep emphasized
         * (larger) words in their balloon, high enough to keep large sound effects separate.
         */
        val minHeightRatio: Float = 0.4f,
        /**
         * Height ratio allowed when the lines touch and the narrower one sits within the wider one,
         * e.g. a large emphasized word directly below normal text.
         */
        val minStackedHeightRatio: Float = 0.3f,
        /** Share of the narrower line's width that must lie within the wider one to count as stacked. */
        val minStackedOverlapRatio: Float = 0.8f,
        /** Maximum gap between stacked lines, relative to the smaller line height. */
        val maxStackedGapRatio: Float = 0.5f,
        /** Height ratio allowed for lines in the same enclosed region. */
        val minSameRegionHeightRatio: Float = 0.25f,
        /** Maximum vertical gap for lines in the same enclosed region, relative to their average height. */
        val maxSameRegionGapRatio: Float = 1.5f,
    )

    /** Returns line groups in reading order (top to bottom, then left to right). */
    fun group(lines: List<TextLine>, regions: RegionLookup = RegionLookup.NONE): List<List<TextLine>> {
        if (lines.isEmpty()) return emptyList()

        val lineRegions = lines.map { regions.regionOf(it.box) }
        val parent = IntArray(lines.size) { it }
        fun find(index: Int): Int {
            var i = index
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }

        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                if (belongTogether(lines[i], lines[j], lineRegions[i], lineRegions[j])) parent[find(j)] = find(i)
            }
        }

        val readingOrder = compareBy<TextLine>({ it.box.top }, { it.box.left })
        return lines.indices
            .groupBy(::find)
            .values
            .map { indices -> indices.map(lines::get).sortedWith(readingOrder) }
            .sortedWith(compareBy({ it.first().box.top }, { it.first().box.left }))
    }

    private fun belongTogether(
        a: TextLine,
        b: TextLine,
        regionA: RegionLookup.Region?,
        regionB: RegionLookup.Region?,
    ): Boolean {
        val heightA = a.box.height
        val heightB = b.box.height
        if (heightA <= 0 || heightB <= 0) return false

        val overlap = a.box.horizontalOverlap(b.box)
        if (overlap <= 0) return false

        // A visible boundary between the lines: they are in different balloons or panels.
        if (regionA != null && regionB != null && regionA.id != regionB.id) return false
        val sameRegion = regionA != null && regionA == regionB && regionA.isEnclosed

        val minHeight = min(heightA, heightB)
        val heightRatio = minHeight.toFloat() / max(heightA, heightB)
        val gap = a.box.verticalGap(b.box)
        val averageHeight = (heightA + heightB) / 2f

        if (sameRegion) {
            return heightRatio >= config.minSameRegionHeightRatio &&
                gap <= averageHeight * config.maxSameRegionGapRatio
        }
        if (isStacked(a, b, overlap, gap, minHeight)) return heightRatio >= config.minStackedHeightRatio
        if (heightRatio < config.minHeightRatio) return false
        return gap <= averageHeight * config.maxLineGapRatio
    }

    private fun isStacked(a: TextLine, b: TextLine, overlap: Int, gap: Int, minHeight: Int): Boolean {
        val narrowerWidth = min(a.box.width, b.box.width)
        if (narrowerWidth <= 0) return false
        return overlap >= narrowerWidth * config.minStackedOverlapRatio &&
            gap <= minHeight * config.maxStackedGapRatio
    }
}
