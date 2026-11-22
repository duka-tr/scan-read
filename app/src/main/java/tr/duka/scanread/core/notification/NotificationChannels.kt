package tr.duka.scanread.core.notification

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import tr.duka.scanread.R

object NotificationChannels {

    const val SCREEN_TRANSLATION = "screen_translation"

    fun registerAll(context: Context) {
        val screenTranslation = NotificationChannelCompat
            .Builder(SCREEN_TRANSLATION, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()

        NotificationManagerCompat.from(context).createNotificationChannel(screenTranslation)
    }
}
