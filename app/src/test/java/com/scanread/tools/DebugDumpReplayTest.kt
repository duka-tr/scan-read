package com.scanread.tools

import com.scanread.image.EnclosedRegionMap
import com.scanread.render.BlockSurfacePlanner
import com.scanread.text.TextBlockBuilder
import com.scanread.text.model.Box
import com.scanread.text.model.TextLine
import com.scanread.text.model.TextWord
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Developer tool: replays a debug capture pulled from a device (see `DebugCaptureDump`) through the
 * text pipeline on the JVM, so grouping rules can be tuned against real screens.
 *
 * Skipped unless the `SCANREAD_DUMP_DIR` environment variable points to a directory containing
 * `analysis_<stamp>.txt` / `capture_<stamp>.png` pairs. Writes `replay_<stamp>.txt` for each pair.
 */
class DebugDumpReplayTest {

    @Test
    fun replay() {
        val directory = System.getenv(DUMP_DIR_ENV)?.let(::File)
        assumeTrue("$DUMP_DIR_ENV not set", directory?.isDirectory == true)

        directory!!.listFiles { file -> file.name.startsWith("analysis_") }.orEmpty().forEach { analysisFile ->
            val stamp = analysisFile.nameWithoutExtension.removePrefix("analysis_")
            val capture = File(directory, "capture_$stamp.png")
            if (capture.exists()) replay(analysisFile, capture, File(directory, "replay_$stamp.txt"))
        }
    }

    private fun replay(analysisFile: File, captureFile: File, output: File) {
        val analysis = analysisFile.readLines()
        val ignoredAreas = analysis.firstOrNull { it.startsWith("ignored") }
            ?.let { BOX.findAll(it).map(::toBox).toList() }
            .orEmpty()
        val lines = parseLines(analysis)

        val (pixels, width, height) = readArgbPixels(captureFile)
        val regions = EnclosedRegionMap(pixels, width, height)

        val blocks = TextBlockBuilder().build(lines, ignoredAreas, regions)
        val surfaces = BlockSurfacePlanner(regions, pixels, width, height).plan(blocks)

        output.writeText(
            buildString {
                appendLine("== Blocks (${blocks.size}), balloons: ${surfaces.count { it.isShaped }} ==")
                blocks.forEachIndexed { index, block ->
                    val lineRegions = block.lines.map { regions.regionOf(it.box)?.let { r -> "${r.id}${if (r.isEnclosed) "" else "o"}" } }
                    val surface = surfaces[index]
                    val kind = if (surface.isShaped) "BALLOON" else "box"
                    appendLine("#$index ${block.box} lineRegions=$lineRegions")
                    appendLine("    $kind text=${surface.textArea.width}x${surface.textArea.height} (orig ${block.box.width}x${block.box.height}) fill=${Integer.toHexString(surface.fillColor)}")
                    regions.regionOf(block.box)?.takeIf { it.isEnclosed }?.let { region ->
                        val shape = regions.shapeOf(region.id)
                        val bounds = shape.bounds
                        val ratio = shape.area.toFloat() / (block.box.width * block.box.height)
                        val offsetX = kotlin.math.abs(block.box.centerX - bounds.centerX).toFloat() / bounds.width
                        val offsetY = kotlin.math.abs(block.box.centerY - bounds.centerY).toFloat() / bounds.height
                        appendLine(
                            "    shape ${bounds.width}x${bounds.height} holes=${"%.2f".format(shape.holeRatio)} " +
                                "shape/text=${"%.1f".format(ratio)} offset=${"%.2f".format(offsetX)},${"%.2f".format(offsetY)}",
                        )
                    }
                    appendLine("    ${block.sourceText}")
                }
            },
        )
    }

    private fun parseLines(analysis: List<String>): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        var current: TextLine? = null
        val section = analysis
            .dropWhile { !it.startsWith("== OCR lines") }
            .drop(1)
            .takeWhile { !it.startsWith("== Blocks") }

        for (row in section) {
            val match = ENTRY.find(row) ?: continue
            val box = toBox(BOX.find(match.groupValues[1])!!)
            val text = match.groupValues[2]
            if (row.startsWith("    ")) {
                current = current?.let { it.copy(words = it.words + TextWord(text, box)) }
            } else {
                current?.let(lines::add)
                current = TextLine(text, box)
            }
        }
        current?.let(lines::add)
        return lines
    }

    /**
     * Reads an image with `javax.imageio`, which exists on the test JVM but not in the Android
     * compile classpath, hence the reflection.
     */
    private fun readArgbPixels(file: File): Triple<IntArray, Int, Int> {
        val image = Class.forName("javax.imageio.ImageIO")
            .getMethod("read", File::class.java)
            .invoke(null, file)
        val imageClass = image.javaClass
        val width = imageClass.getMethod("getWidth").invoke(image) as Int
        val height = imageClass.getMethod("getHeight").invoke(image) as Int
        val pixels = IntArray(width * height)
        imageClass
            .getMethod(
                "getRGB",
                Int::class.java, Int::class.java, Int::class.java, Int::class.java,
                IntArray::class.java, Int::class.java, Int::class.java,
            )
            .invoke(image, 0, 0, width, height, pixels, 0, width)
        return Triple(pixels, width, height)
    }

    private fun toBox(match: MatchResult): Box {
        val (left, top, right, bottom) = match.destructured
        return Box(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    private companion object {
        const val DUMP_DIR_ENV = "SCANREAD_DUMP_DIR"
        val BOX = Regex("Box\\(left=(-?\\d+), top=(-?\\d+), right=(-?\\d+), bottom=(-?\\d+)\\)")
        val ENTRY = Regex("^\\s*(Box\\([^)]*\\)) \"(.*)\"$")
    }
}
