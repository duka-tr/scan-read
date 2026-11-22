package com.scanread.pipeline

import android.graphics.Bitmap
import com.scanread.image.EnclosedRegionMap
import com.scanread.ocr.OcrEngine
import com.scanread.render.BlockSurface
import com.scanread.render.BlockSurfacePlanner
import com.scanread.text.TextBlockBuilder
import com.scanread.text.model.Box
import com.scanread.text.model.TextBlock
import com.scanread.text.model.TextLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Finds the text blocks in a screen capture and plans how each will be covered and redrawn.
 * OCR and enclosed-region detection run in parallel; lines are then split, grouped and normalized.
 */
class ScreenTextAnalyzer(
    private val ocrEngine: OcrEngine,
    private val blockBuilder: TextBlockBuilder = TextBlockBuilder(),
) : Closeable {

    data class Result(
        /** Lines exactly as returned by the OCR engine. */
        val lines: List<TextLine>,
        val blocks: List<TextBlock>,
        /** Drawing plan per block, in the same order as [blocks]. */
        val surfaces: List<BlockSurface>,
    )

    private class Image(val pixels: IntArray, val width: Int, val height: Int, val regions: EnclosedRegionMap)

    /** @param ignoredAreas screen areas whose text is skipped, e.g. system bars. */
    suspend fun analyze(bitmap: Bitmap, ignoredAreas: List<Box> = emptyList()): Result = coroutineScope {
        val image = async(Dispatchers.Default) { bitmap.toImage() }
        val lines = ocrEngine.recognize(bitmap)
        withContext(Dispatchers.Default) {
            val analyzed = image.await()
            val blocks = blockBuilder.build(lines, ignoredAreas, analyzed.regions)
            val surfaces = BlockSurfacePlanner(analyzed.regions, analyzed.pixels, analyzed.width, analyzed.height)
                .plan(blocks)
            Result(lines, blocks, surfaces)
        }
    }

    override fun close() = ocrEngine.close()

    private fun Bitmap.toImage(): Image {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return Image(pixels, width, height, EnclosedRegionMap(pixels, width, height))
    }
}
