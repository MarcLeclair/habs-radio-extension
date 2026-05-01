package io.github.habsradiosync.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import io.github.habsradiosync.shared.RemotePaths

object RemoteNotificationController {
    private const val CHANNEL_ID = "remote-controls"
    private const val NOTIFICATION_ID = 986

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Remote controls", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun show(
        context: Context,
        isPlaying: Boolean,
        delaySeconds: Int,
        volumePercent: Int,
        message: String? = null,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(context, isPlaying, delaySeconds, volumePercent, message))
    }

    private fun notification(
        context: Context,
        isPlaying: Boolean,
        delaySeconds: Int,
        volumePercent: Int,
        message: String?,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)

        val state = "${if (isPlaying) "Playing" else "Not playing"} | Delay ${delaySeconds}s | Vol $volumePercent%"
        return builder
            .setContentTitle("Habs Radio Remote")
            .setContentText(message ?: state)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent(context))
            .setOngoing(false)
            .addAction(
                action(
                    context,
                    android.R.drawable.ic_media_play,
                    "Play",
                    commandIntent(context, RemotePaths.PLAY, 1)
                )
            )
            .addAction(
                action(
                    context,
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    commandIntent(context, RemotePaths.PAUSE, 2)
                )
            )
            .addAction(
                action(
                    context,
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    commandIntent(context, RemotePaths.STOP, 3)
                )
            )
            .addAction(
                action(
                    context,
                    android.R.drawable.ic_popup_sync,
                    "Reload",
                    commandIntent(context, RemotePaths.RELOAD, 4)
                )
            )
            .addAction(
                action(
                    context,
                    android.R.drawable.ic_media_rew,
                    "-5s",
                    commandIntent(context, RemotePaths.nudge(-5.0), 5)
                )
            )
            .addAction(
                action(
                    context,
                    android.R.drawable.ic_media_ff,
                    "+5s",
                    commandIntent(context, RemotePaths.nudge(5.0), 6)
                )
            )
            .build()
    }

    private fun action(context: Context, icon: Int, title: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent).build()

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun commandIntent(context: Context, path: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RemoteCommandReceiver::class.java)
                .putExtra(RemoteCommandReceiver.EXTRA_PATH, path),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
