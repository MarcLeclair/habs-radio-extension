package io.github.cococraft.puckradiosync.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class PlaybackNotificationController(private val context: Context) {
    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Radio playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun update(text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    fun notification(text: String): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Puck Radio Sync TV")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

    companion object {
        const val NOTIFICATION_ID = 985
        private const val CHANNEL_ID = "radio-playback"
    }
}
