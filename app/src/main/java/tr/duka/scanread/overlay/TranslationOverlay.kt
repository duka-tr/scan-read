package tr.duka.scanread.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.withTranslation
import tr.duka.scanread.R
import tr.duka.scanread.text.model.Box
import java.util.Locale

/**
 * Full-screen overlay that covers the original text and draws translations in its place.
 * Coordinates are in screen pixels, matching the screen capture.
 *
 * Any touch dismisses it ([onDismissRequest]); the touch is not passed on, because Android shows
 * touch-transparent overlays of other apps at most 80% opaque, which would let the original text
 * show through. The service also dismisses it when the screen underneath changes.
 */
class TranslationOverlay(
    private val context: Context,
    /** Language of the translations; used for hyphenation. */
    textLocale: Locale,
    private val onDismissRequest: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private val view = TranslationView(context, textLocale).apply {
        contentDescription = context.getString(R.string.overlay_content_description)
        setOnClickListener { onDismissRequest() }
    }

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    var isShowing: Boolean = false
        private set

    /** Shows [items] and returns the screen areas the overlay draws on. */
    fun show(items: List<TranslationRenderer.Item>): List<Box> {
        view.setItems(items)
        if (!isShowing) {
            windowManager.addView(view, layoutParams)
            isShowing = true
        }
        return view.drawnAreas
    }

    fun dismiss() {
        if (!isShowing) return
        windowManager.removeViewImmediate(view)
        view.setItems(emptyList())
        isShowing = false
    }
}

// Only created in code, never inflated from XML, so the standard view constructors are not needed.
@SuppressLint("ViewConstructor")
private class TranslationView(context: Context, textLocale: Locale) : View(context) {

    private val renderer = TranslationRenderer(context, textLocale)
    private val screenLocation = IntArray(2)
    private var prepared: List<TranslationRenderer.Prepared> = emptyList()

    val drawnAreas: List<Box> get() = prepared.map { it.drawnBounds }

    fun setItems(items: List<TranslationRenderer.Item>) {
        prepared.forEach { it.recycle() }
        prepared = items.map(renderer::prepare)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility") // The touch is forwarded through performClick().
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // React on touch-down: a swipe meant to scroll the page removes the translation right away.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The window may not start at the screen origin; shift so screen coordinates line up.
        getLocationOnScreen(screenLocation)
        canvas.withTranslation(-screenLocation[0].toFloat(), -screenLocation[1].toFloat()) {
            renderer.draw(this, prepared)
        }
    }
}
