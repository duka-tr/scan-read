package com.scanread.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread

/**
 * Mirrors the screen into an [ImageReader] and keeps the most recent frame so that
 * [captureLatest] can return it instantly.
 *
 * A [MediaProjection.Callback] must be registered on [projection] before [start] is called
 * (required since Android 14).
 */
class ScreenCapturer(
    private val context: Context,
    private val projection: MediaProjection,
) {
    private val thread = HandlerThread("ScreenCapture").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var metrics: ScreenMetrics? = null

    // Guarded by [lock].
    private var latestImage: Image? = null

    fun start() {
        check(virtualDisplay == null) { "ScreenCapturer already started" }

        val current = ScreenMetrics.current(context)
        val reader = createReader(current)
        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            current.width,
            current.height,
            current.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        imageReader = reader
        metrics = current
    }

    /** Re-creates the capture surface when the screen size changes (e.g. rotation). */
    fun onScreenSizeChanged() {
        val display = virtualDisplay ?: return
        val current = ScreenMetrics.current(context)
        if (current == metrics) return

        val newReader = createReader(current)
        display.resize(current.width, current.height, current.densityDpi)
        display.surface = newReader.surface

        val oldReader = imageReader
        imageReader = newReader
        metrics = current
        oldReader?.let(::closeReader)
    }

    /** Returns a copy of the most recent frame, or `null` if no frame has arrived yet. */
    fun captureLatest(): Bitmap? = synchronized(lock) {
        latestImage?.toBitmap()
    }

    /**
     * Luminance grid of the most recent frame (see [ScreenChangeDetector.grid]), read straight from
     * the frame buffer without copying the image, or `null` if no frame has arrived yet.
     */
    fun latestLuminanceGrid(detector: ScreenChangeDetector): IntArray? = synchronized(lock) {
        val image = latestImage ?: return null
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        // RGBA_8888: bytes are R, G, B, A.
        detector.grid { x, y ->
            val offset = y * rowStride + x * pixelStride
            val r = buffer.get(offset).toInt() and 0xFF
            val g = buffer.get(offset + 1).toInt() and 0xFF
            val b = buffer.get(offset + 2).toInt() and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.let(::closeReader)
        imageReader = null
        thread.quitSafely()
    }

    private fun createReader(metrics: ScreenMetrics): ImageReader =
        ImageReader.newInstance(metrics.width, metrics.height, PixelFormat.RGBA_8888, MAX_IMAGES).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                synchronized(lock) {
                    latestImage?.close()
                    latestImage = image
                }
            }, handler)
        }

    private fun closeReader(reader: ImageReader) {
        reader.setOnImageAvailableListener(null, null)
        synchronized(lock) {
            latestImage?.close()
            latestImage = null
        }
        reader.close()
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer.apply { rewind() }
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width

        // Rows may be padded; decode at padded width, then crop to the real width.
        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) return padded

        return Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
    }

    private companion object {
        const val VIRTUAL_DISPLAY_NAME = "ScanReadCapture"
        const val MAX_IMAGES = 3
    }
}
