package io.github.cococraft.puckradiosync.remote

import io.github.cococraft.puckradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.cococraft.puckradiosync.shared.RadioState
import io.github.cococraft.puckradiosync.shared.RemotePaths
import io.github.cococraft.puckradiosync.shared.radioStateFromJson
import java.net.HttpURLConnection
import java.net.URL

class RemoteClient(
    private val host: String,
    private val port: Int = HRS_DEFAULT_CONTROL_PORT,
) {
    fun send(path: String): String =
        request(path)

    fun state(): RadioState =
        radioStateFromJson(request(RemotePaths.STATE))

    fun sendAndFetchState(path: String): RadioState {
        request(path)
        return state()
    }

    private fun request(path: String): String {
        val connection = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = REQUEST_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 1500
    }
}
