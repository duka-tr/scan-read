package tr.duka.scanread.ocr

import android.graphics.Bitmap
import tr.duka.scanread.text.model.TextLine
import java.io.Closeable

/** Recognizes text lines in an image. Implementations must run fully on-device. */
interface OcrEngine : Closeable {
    suspend fun recognize(bitmap: Bitmap): List<TextLine>
}
