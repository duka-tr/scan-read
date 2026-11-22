package tr.duka.scanread.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import tr.duka.scanread.core.tasks.await
import tr.duka.scanread.text.model.Box
import tr.duka.scanread.text.model.TextLine
import tr.duka.scanread.text.model.TextWord

/** [OcrEngine] backed by the bundled ML Kit Latin-script model. */
class MlKitOcrEngine : OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): List<TextLine> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox?.toBox() ?: return@mapNotNull null
                TextLine(
                    text = line.text,
                    box = box,
                    words = line.elements.mapNotNull { element ->
                        element.boundingBox?.toBox()?.let { TextWord(element.text, it) }
                    },
                )
            }
    }

    private fun Rect.toBox() = Box(left, top, right, bottom)

    override fun close() = recognizer.close()
}
