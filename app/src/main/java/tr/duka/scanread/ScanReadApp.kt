package tr.duka.scanread

import android.app.Application
import tr.duka.scanread.core.locale.SystemLanguage
import tr.duka.scanread.core.notification.NotificationChannels
import tr.duka.scanread.core.settings.SettingsRepository
import tr.duka.scanread.translate.opusmt.HighQualityModels
import tr.duka.scanread.translate.opusmt.OpusMtModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScanReadApp : Application() {

    /** Work that must outlive screens, such as model downloads. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val highQualityModels: HighQualityModels by lazy {
        HighQualityModels(
            store = OpusMtModelStore(filesDir, BuildConfig.MODEL_RELEASES_URL),
            scope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        SystemLanguage.init(this)
        // Re-registering on every start keeps channel names in sync with the current locale.
        NotificationChannels.registerAll(this)
    }
}
