package tr.duka.scanread.core.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * The phone's language, as opposed to the language chosen for ScanRead's own screens.
 *
 * With a per-app language (Android 13+), `Locale.getDefault()` and even the system `Resources`
 * report the app's language; only [LocaleManager.getSystemLocales] keeps the phone's.
 */
object SystemLanguage {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun locale(): Locale {
        val context = appContext
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.systemLocales
            if (locales != null && !locales.isEmpty) return locales[0]
        }
        return Locale.getDefault()
    }
}
