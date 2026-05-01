package io.github.cococraft.puckradiosync.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.cococraft.puckradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class RemoteCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val prefs = context.getSharedPreferences(RemoteTargetStore.PREFS_NAME, Context.MODE_PRIVATE)
        val host = prefs.getString(RemoteTargetStore.KEY_HOST, "").orEmpty().trim()
        val port = prefs.getString(RemoteTargetStore.KEY_PORT, HRS_DEFAULT_CONTROL_PORT.toString())
            ?.trim()
            ?.toIntOrNull()
            ?: HRS_DEFAULT_CONTROL_PORT
        if (host.isBlank()) return

        val pendingResult = goAsync()
        thread {
            try {
                val state = runCatching {
                    RemoteClient(host, port).sendAndFetchState(path)
                }.getOrNull()
                if (state == null) {
                    RemoteNotificationController.show(context, false, 0, 100, "TV unreachable at $host:$port")
                } else {
                    RemoteNotificationController.show(
                        context = context,
                        isPlaying = state.playing,
                        delaySeconds = state.delaySeconds.roundToInt(),
                        volumePercent = state.volumePercent,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
    companion object {
        const val EXTRA_PATH = "io.github.cococraft.puckradiosync.remote.EXTRA_PATH"
    }
}
