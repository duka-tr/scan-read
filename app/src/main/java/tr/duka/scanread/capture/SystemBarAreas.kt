package tr.duka.scanread.capture

import android.content.Context
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import tr.duka.scanread.text.model.Box

/** Screen areas covered by system UI (status bar, navigation bar, display cutout), in screen pixels. */
object SystemBarAreas {

    fun current(context: Context): List<Box> {
        // Window insets of a non-visual context are only available on Android 11+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()

        val metrics = runCatching {
            context.getSystemService(WindowManager::class.java).maximumWindowMetrics
        }.getOrNull() ?: return emptyList()

        val insets = metrics.windowInsets.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val width = metrics.bounds.width()
        val height = metrics.bounds.height()

        return buildList {
            if (insets.top > 0) add(Box(0, 0, width, insets.top))
            if (insets.bottom > 0) add(Box(0, height - insets.bottom, width, height))
            if (insets.left > 0) add(Box(0, 0, insets.left, height))
            if (insets.right > 0) add(Box(width - insets.right, 0, width, height))
        }
    }
}
