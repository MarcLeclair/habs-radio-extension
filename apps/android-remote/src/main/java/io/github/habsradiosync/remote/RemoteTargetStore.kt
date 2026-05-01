package io.github.habsradiosync.remote

import android.content.Context
import androidx.core.content.edit
import io.github.habsradiosync.shared.HRS_DEFAULT_CONTROL_PORT

class RemoteTargetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): RemoteTarget =
        RemoteTarget(
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getString(KEY_PORT, HRS_DEFAULT_CONTROL_PORT.toString())
                ?: HRS_DEFAULT_CONTROL_PORT.toString(),
            source = prefs.getString(KEY_SOURCE, RemoteTargetSource.MANUAL) ?: RemoteTargetSource.MANUAL,
        )

    fun save(host: String, port: String, source: String) {
        prefs.edit {
            putString(KEY_HOST, host)
            putString(KEY_PORT, port)
            putString(KEY_SOURCE, source)
            putLong(KEY_LAST_CONNECTED_AT, System.currentTimeMillis())
        }
    }

    companion object {
        const val PREFS_NAME = "remote-control"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_SOURCE = "source"
        const val KEY_LAST_CONNECTED_AT = "last_connected_at"
    }
}
