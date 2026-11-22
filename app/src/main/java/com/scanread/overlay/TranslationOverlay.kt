package com.scanread.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.withTranslation
import com.scanread.R
import java.util.Locale

/**
 * Full-screen overlay that covers the original text and draws translations in its place.
 * Tapping it dismisses it. Coordinates are in screen pixels, matching the screen capture.
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

    fun show(items: List<TranslationRenderer.Item>) {
        view.setItems(items)
        if (!isShowing) {
            windowManager.addView(view, layoutParams)
            isShowing = true
        }
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

    fun setItems(items: List<TranslationRenderer.Item>) {
        prepared.forEach { it.recycle() }
        prepared = items.map(renderer::prepare)
        invalidate()
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
