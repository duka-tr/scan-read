package com.scanread

import android.app.Application
import com.scanread.core.notification.NotificationChannels
import com.scanread.translate.mlkit.defaultLanguagePair
import com.scanread.translate.opusmt.HighQualityModels
import com.scanread.translate.opusmt.OpusMtModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScanReadApp : Application() {

    /** Work that must outlive screens, such as model downloads. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val highQualityModels: HighQualityModels by lazy {
        HighQualityModels(
            store = OpusMtModelStore(filesDir, BuildConfig.MODEL_RELEASES_URL),
            pair = defaultLanguagePair(),
            scope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Re-registering on every start keeps channel names in sync with the current locale.
        NotificationChannels.registerAll(this)
    }
}
