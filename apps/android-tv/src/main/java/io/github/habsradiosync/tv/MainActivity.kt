package io.github.habsradiosync.tv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import io.github.habsradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.habsradiosync.shared.RemotePaths
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

@UnstableApi
class MainActivity : ComponentActivity() {
    private var titleTapCount = 0
    private var diagnosticsEnabled by mutableStateOf(false)
    private var statusBody by mutableStateOf("")
    private var debugToast: Toast? = null
    private val debugKeyBuffer = ArrayDeque<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val statusPoller = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, RadioPlaybackService::class.java))
        statusBody = getString(R.string.status_starting)
        setContent {
            TvApp(
                statusBody = statusBody,
                diagnosticsEnabled = diagnosticsEnabled,
                onPlay = ::play,
                onPause = ::pause,
                onStop = ::stop,
                onRefresh = ::refreshStatus,
                onSecretTap = ::toggleDiagnosticsAfterSecretTap,
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            if (trackDebugKeySequence(event.keyCode)) return true
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    pause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    stop()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusPoller)
    }

    override fun onPause() {
        handler.removeCallbacks(statusPoller)
        super.onPause()
    }

    private fun play() {
        startForegroundService(
            Intent(this, RadioPlaybackService::class.java)
                .setAction(RadioPlaybackService.ACTION_PLAY)
        )
        statusBody = getString(R.string.status_buffering)
    }

    private fun pause() {
        startService(
            Intent(this, RadioPlaybackService::class.java)
                .setAction(RadioPlaybackService.ACTION_PAUSE)
        )
        statusBody = getString(R.string.status_paused)
    }

    private fun stop() {
        startService(
            Intent(this, RadioPlaybackService::class.java)
                .setAction(RadioPlaybackService.ACTION_STOP)
        )
        statusBody = getString(R.string.status_stopped)
    }

    private fun toggleDiagnosticsAfterSecretTap() {
        titleTapCount += 1
        val remainingTaps = DEBUG_TAP_THRESHOLD - titleTapCount
        if (remainingTaps > 0) {
            showDebugToast("Press $remainingTaps more ${if (remainingTaps == 1) "time" else "times"} for debug")
            return
        }

        titleTapCount = 0
        toggleDiagnostics()
    }

    private fun trackDebugKeySequence(keyCode: Int): Boolean {
        if (keyCode !in DEBUG_KEY_SEQUENCE) {
            debugKeyBuffer.clear()
            return false
        }

        debugKeyBuffer.addLast(keyCode)
        while (debugKeyBuffer.size > DEBUG_KEY_SEQUENCE.size) debugKeyBuffer.removeFirst()
        if (debugKeyBuffer.toList() != DEBUG_KEY_SEQUENCE) return false

        debugKeyBuffer.clear()
        toggleDiagnostics()
        return true
    }

    private fun toggleDiagnostics() {
        diagnosticsEnabled = !diagnosticsEnabled
        DebugOptions.enabled = diagnosticsEnabled
        statusBody = getString(if (diagnosticsEnabled) R.string.debug_enabled else R.string.debug_disabled)
        showDebugToast(if (diagnosticsEnabled) "Debug diagnostics enabled" else "Debug diagnostics disabled")
    }

    private fun showDebugToast(message: String) {
        debugToast?.cancel()
        debugToast = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun refreshStatus() {
        thread {
            val result = runCatching {
                val connection = URL("http://127.0.0.1:$HRS_DEFAULT_CONTROL_PORT${RemotePaths.STATE}")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 1000
                connection.readTimeout = 1000
                connection.inputStream.bufferedReader().use { it.readText() }
            }.getOrElse {
                getString(R.string.status_server_unavailable)
            }
            runOnUiThread { statusBody = result }
        }
    }

    private companion object {
        const val DEBUG_TAP_THRESHOLD = 5
        val DEBUG_KEY_SEQUENCE = listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}
