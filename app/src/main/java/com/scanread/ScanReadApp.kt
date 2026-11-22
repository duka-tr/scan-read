package com.scanread

import android.app.Application
import com.scanread.core.notification.NotificationChannels

class ScanReadApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Re-registering on every start keeps channel names in sync with the current locale.
        NotificationChannels.registerAll(this)
    }
}
