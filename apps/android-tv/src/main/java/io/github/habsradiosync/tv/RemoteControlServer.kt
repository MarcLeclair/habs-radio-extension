package io.github.habsradiosync.tv

import io.github.habsradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.habsradiosync.shared.RadioState
import io.github.habsradiosync.shared.RemoteCommand
import io.github.habsradiosync.shared.toJson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RemoteControlServer(
    private val getState: () -> RadioState,
    private val onCommand: (RemoteCommand) -> RadioState,
    private val port: Int = HRS_DEFAULT_CONTROL_PORT,
    private val onError: (String) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var socket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "hrs-control-server") {
            try {
                socket = ServerSocket(port)
                while (running.get()) {
                    val client = socket?.accept() ?: break
                    thread(name = "hrs-control-client") {
                        client.use(::handleClient)
                    }
                }
            } catch (error: BindException) {
                onError("Port $port is already in use")
                running.set(false)
            } catch (error: Exception) {
                if (running.get()) onError(error.message ?: error::class.simpleName.orEmpty())
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
    }

    private fun handleClient(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val request = reader.readLine().orEmpty()
        val path = request.split(" ").getOrNull(1).orEmpty()
        val response = route(path)
        val bytes = response.body.encodeToByteArray()
        client.getOutputStream().use { out ->
            out.write("HTTP/1.1 ${response.status}\r\n".encodeToByteArray())
            out.write("Content-Type: application/json\r\n".encodeToByteArray())
            out.write("Access-Control-Allow-Origin: *\r\n".encodeToByteArray())
            out.write("Content-Length: ${bytes.size}\r\n".encodeToByteArray())
            out.write("Connection: close\r\n\r\n".encodeToByteArray())
            out.write(bytes)
        }
    }

    private fun route(path: String): HttpResponse {
        val route = path.substringBefore("?")
        val params = parseQuery(path.substringAfter("?", ""))
        val state = when (route) {
            "/state" -> getState()
            "/play" -> onCommand(RemoteCommand.Play)
            "/pause" -> onCommand(RemoteCommand.Pause)
            "/stop" -> onCommand(RemoteCommand.Stop)
            "/reload" -> onCommand(RemoteCommand.Reload)
            "/delay" -> onCommand(RemoteCommand.SetDelay(params["seconds"]?.toDoubleOrNull() ?: 0.0))
            "/nudge" -> onCommand(RemoteCommand.NudgeDelay(params["seconds"]?.toDoubleOrNull() ?: 0.0))
            "/volume" -> onCommand(RemoteCommand.SetVolume(params["percent"]?.toIntOrNull() ?: 100))
            "/volumeNudge" -> onCommand(RemoteCommand.NudgeVolume(params["percent"]?.toIntOrNull() ?: 0))
            "/station" -> onCommand(RemoteCommand.SetStation(params["id"].orEmpty()))
            else -> return HttpResponse("404 Not Found", "{\"error\":\"not found\"}")
        }
        return HttpResponse("200 OK", state.toJson())
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&")
            .filter { it.contains("=") }
            .associate {
                val key = it.substringBefore("=")
                val value = it.substringAfter("=")
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }

    private data class HttpResponse(val status: String, val body: String)
}
