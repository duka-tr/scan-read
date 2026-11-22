package com.scanread.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import com.scanread.R
import com.scanread.capture.ScreenMetrics
import kotlin.math.abs

/**
 * Draggable floating button drawn above other apps. Snaps to the nearest screen edge
 * after dragging; a tap without dragging triggers [onClick].
 */
class FloatingButton(
    private val context: Context,
    private val onClick: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val sizePx = context.resources.getDimensionPixelSize(R.dimen.floating_button_size)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val view: ImageView = ImageView(context).apply {
        val padding = resources.getDimensionPixelSize(R.dimen.floating_button_padding)
        setPadding(padding, padding, padding, padding)
        setBackgroundResource(R.drawable.bg_floating_button)
        setImageResource(R.drawable.ic_translate)
        contentDescription = context.getString(R.string.floating_button_content_description)
        setOnClickListener { onClick() }
        setOnTouchListener(DragListener())
    }

    private val layoutParams = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = ScreenMetrics.current(context).height / 3
    }

    private var isAttached = false

    fun show() {
        if (isAttached) return
        windowManager.addView(view, layoutParams)
        isAttached = true
    }

    fun remove() {
        if (!isAttached) return
        windowManager.removeViewImmediate(view)
        isAttached = false
    }

    /** Hides the button without removing it, e.g. so it does not appear in a screen capture. */
    fun setHidden(hidden: Boolean) {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** Dims the button while a capture is being processed. */
    fun setBusy(busy: Boolean) {
        view.alpha = if (busy) BUSY_ALPHA else 1f
    }

    private fun snapToEdge() {
        val screenWidth = ScreenMetrics.current(context).width
        val center = layoutParams.x + sizePx / 2
        layoutParams.x = if (center < screenWidth / 2) 0 else screenWidth - sizePx
        windowManager.updateViewLayout(view, layoutParams)
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility") // Taps are forwarded through performClick().
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) isDragging = true
                    if (isDragging) {
                        layoutParams.x = startX + dx.toInt()
                        layoutParams.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, layoutParams)
                    }
                }

                MotionEvent.ACTION_UP -> if (isDragging) snapToEdge() else v.performClick()

                MotionEvent.ACTION_CANCEL -> if (isDragging) snapToEdge()
            }
            return true
        }
    }

    private companion object {
        const val BUSY_ALPHA = 0.5f
    }
}
