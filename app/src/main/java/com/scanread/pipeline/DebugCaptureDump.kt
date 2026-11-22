package com.scanread.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.scanread.text.model.Box
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug builds only: keeps the most recent captures and their analysis in the app's private
 * storage (`files/debug/`), so they can be pulled with `adb exec-out run-as <package> …` and
 * replayed on the JVM (see `DebugDumpReplayTest`).
 */
class DebugCaptureDump(context: Context) {

    private val directory = File(context.filesDir, DIRECTORY_NAME)

    fun write(
        bitmap: Bitmap,
        ignoredAreas: List<Box>,
        result: ScreenTextAnalyzer.Result,
        translations: List<TranslatedBlock>,
        elapsedMs: Long,
    ) {
        directory.mkdirs()
        val stamp = SimpleDateFormat(STAMP_FORMAT, Locale.ROOT).format(Date())

        File(directory, "$CAPTURE_PREFIX$stamp.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$ANALYSIS_PREFIX$stamp.txt").writeText(
            buildString {
                appendLine("capture ${bitmap.width}x${bitmap.height}, analysis $elapsedMs ms")
                appendLine("ignored $ignoredAreas")
                appendLine()
                appendLine("== OCR lines (${result.lines.size}) ==")
                result.lines.forEach { line ->
                    appendLine("${line.box} \"${line.text}\"")
                    line.words.forEach { appendLine("    ${it.box} \"${it.text}\"") }
                }
                appendLine()
                appendLine("== Blocks (${result.blocks.size}) ==")
                result.blocks.forEachIndexed { index, block ->
                    appendLine("#$index ${block.box} caps=${block.isAllCaps}")
                    appendLine("    source: ${block.sourceText}")
                    appendLine("    text:   ${block.text}")
                    translations.firstOrNull { it.block == block }?.let { appendLine("    tr:     ${it.displayText}") }
                }
            },
        )
        deleteOldDumps()
    }

    private fun deleteOldDumps() {
        val files = directory.listFiles().orEmpty()
        listOf(CAPTURE_PREFIX, ANALYSIS_PREFIX).forEach { prefix ->
            files.filter { it.name.startsWith(prefix) }
                .sortedByDescending { it.name }
                .drop(MAX_DUMPS)
                .forEach(File::delete)
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "debug"
        const val CAPTURE_PREFIX = "capture_"
        const val ANALYSIS_PREFIX = "analysis_"
        const val STAMP_FORMAT = "yyyyMMdd_HHmmss_SSS"
        const val MAX_DUMPS = 10
    }
}
