package com.scanread.capture

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Physical size of the whole display, including system bars. */
data class ScreenMetrics(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
) {
    companion object {
        fun current(context: Context): ScreenMetrics {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val densityDpi = context.resources.displayMetrics.densityDpi

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.maximumWindowMetrics.bounds
                ScreenMetrics(bounds.width(), bounds.height(), densityDpi)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                ScreenMetrics(metrics.widthPixels, metrics.heightPixels, densityDpi)
            }
        }
    }
}
