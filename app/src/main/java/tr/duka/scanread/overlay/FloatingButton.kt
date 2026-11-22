package tr.duka.scanread.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import tr.duka.scanread.R
import tr.duka.scanread.capture.ScreenMetrics
import tr.duka.scanread.text.model.Box
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
    private var sizePx = context.resources.getDimensionPixelSize(R.dimen.floating_button_size)
    private var opacity = 1f
    private var busy = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val view: ImageView = ImageView(context).apply {
        val padding = (sizePx * ICON_PADDING_SHARE).toInt()
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

    /** Where the button currently is on screen. */
    val screenBounds: Box
        get() = Box(layoutParams.x, layoutParams.y, layoutParams.x + sizePx, layoutParams.y + sizePx)

    /** Dims the button while a capture is being processed. */
    fun setBusy(busy: Boolean) {
        this.busy = busy
        updateAlpha()
    }

    /** Applies the user's size (dp) and opacity (0..1) settings. */
    fun setAppearance(sizeDp: Int, opacity: Float) {
        this.opacity = opacity
        updateAlpha()
        val newSize = (sizeDp * context.resources.displayMetrics.density).toInt()
        if (newSize == sizePx) return
        sizePx = newSize
        val padding = (newSize * ICON_PADDING_SHARE).toInt()
        view.setPadding(padding, padding, padding, padding)
        layoutParams.width = newSize
        layoutParams.height = newSize
        if (isAttached) snapToEdge()
    }

    private fun updateAlpha() {
        view.alpha = opacity * if (busy) BUSY_ALPHA else 1f
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
        const val ICON_PADDING_SHARE = 0.25f
    }
}
