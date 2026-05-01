package io.github.habsradiosync.remote

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.habsradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.habsradiosync.shared.PlaybackStatus
import io.github.habsradiosync.shared.RadioState
import io.github.habsradiosync.shared.RemotePaths
import io.github.habsradiosync.shared.radioStateFromJson
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import java.util.concurrent.Executor

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val handler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> handler.post(command) }
    private val targetStore = RemoteTargetStore(application)
    private val discovery = RemoteDiscovery(
        context = application,
        mainExecutor = mainExecutor,
        onEvent = ::handleDiscoveryEvent,
    )
    private val statePoller = object : Runnable {
        override fun run() {
            if (uiState.host.trim().isNotBlank()) {
                send(RemotePaths.STATE, updateStatusOnError = false)
            }
            handler.postDelayed(this, STATE_POLL_MS)
        }
    }
    private val discoveryRetry = object : Runnable {
        override fun run() {
            startDiscovery()
        }
    }

    var uiState by mutableStateOf(RemoteUiState())
        private set

    private var titleTapCount = 0
    private var userEditedTarget = false

    init {
        restoreRemoteTarget()
        updateRemoteNotification()
    }

    fun onResume() {
        startDiscovery()
        handler.post(statePoller)
    }

    fun onPause() {
        handler.removeCallbacks(statePoller)
        stopDiscovery()
    }

    fun onHostChange(host: String) {
        uiState = uiState.copy(host = host, targetSource = RemoteTargetSource.MANUAL)
        userEditedTarget = true
        persistRemoteTarget()
        updateRemoteNotification()
    }

    fun onPortChange(port: String) {
        uiState = uiState.copy(port = port, targetSource = RemoteTargetSource.MANUAL)
        userEditedTarget = true
        persistRemoteTarget()
        updateRemoteNotification()
    }

    fun send(path: String, updateStatusOnError: Boolean = true) {
        val targetHost = uiState.host.trim()
        val targetPort = uiState.port.trim().toIntOrNull() ?: HRS_DEFAULT_CONTROL_PORT
        if (targetHost.isBlank()) {
            if (updateStatusOnError) {
                uiState = uiState.copy(statusText = "Enter the TV IP address or wait for discovery to find your TV.")
            }
            return
        }

        thread {
            val result = runCatching {
                RemoteClient(targetHost, targetPort).send(path)
            }
            handler.post {
                result
                    .onSuccess {
                        persistRemoteTarget()
                        renderStatus(it)
                    }
                    .onFailure { error ->
                        if (updateStatusOnError) {
                            uiState = uiState.copy(
                                statusText = "TV unreachable at $targetHost:$targetPort. Make sure the TV app is open and both devices are on the same network. ${error.message.orEmpty()}",
                                playbackStatus = PlaybackStatus.Error,
                            )
                        }
                    }
            }
        }
    }

    fun handleSecretTap() {
        titleTapCount += 1
        if (titleTapCount < 5) return

        titleTapCount = 0
        val enabled = !uiState.diagnosticsEnabled
        uiState = uiState.copy(
            diagnosticsEnabled = enabled,
            statusText = if (enabled) "Debug diagnostics enabled" else "Debug diagnostics hidden",
        )
    }

    fun startDiscovery() {
        discovery.start()
    }

    fun stopDiscovery() {
        handler.removeCallbacks(discoveryRetry)
        discovery.stop()
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        if (Build.VERSION.SDK_INT < 33 || grants[android.Manifest.permission.NEARBY_WIFI_DEVICES] == true) {
            startDiscovery()
        }
        updateRemoteNotification()
    }

    fun updateRemoteNotification() {
        RemoteNotificationController.show(
            context = getApplication(),
            isPlaying = uiState.isPlaying,
            delaySeconds = uiState.delaySeconds,
            volumePercent = uiState.volumePercent,
        )
    }

    override fun onCleared() {
        handler.removeCallbacks(statePoller)
        stopDiscovery()
        super.onCleared()
    }

    private fun renderStatus(body: String) {
        runCatching {
            renderState(radioStateFromJson(body))
        }.getOrElse {
            uiState = uiState.copy(statusText = body)
            updateRemoteNotification()
        }
    }

    private fun renderState(state: RadioState) {
        val delay = state.delaySeconds
        val available = state.delayAvailableSeconds
        uiState = uiState.copy(
            delaySeconds = delay.roundToInt(),
            availableBufferSeconds = available.roundToInt(),
            volumePercent = state.volumePercent,
            isPlaying = state.playing,
            playbackStatus = state.status,
            audioBytesWritten = state.audioBytesWritten,
            audioBytesPerSecond = state.audioBytesPerSecond,
            isLoadingDelay = delay > available + 0.05,
            statusText = buildString {
                append(state.status.name)
                append(if (state.playing) " | Playing" else " | Not playing")
                state.error?.takeIf { it.isNotBlank() }?.let {
                    append("\n")
                    append(it)
                }
            },
        )
        updateRemoteNotification()
    }

    private fun restoreRemoteTarget() {
        val target = targetStore.load()
        uiState = uiState.copy(
            host = target.host,
            port = target.port,
            targetSource = target.source,
        )
        userEditedTarget = false
    }

    private fun persistRemoteTarget() {
        targetStore.save(uiState.host, uiState.port, uiState.targetSource)
    }

    private fun handleDiscoveryEvent(event: RemoteDiscoveryEvent) {
        handler.post {
            when (event) {
                RemoteDiscoveryEvent.Searching -> {
                    uiState = uiState.copy(statusText = "Looking for the TV app on this Wi-Fi...")
                }
                is RemoteDiscoveryEvent.Found -> {
                    uiState = uiState.copy(statusText = "Found ${event.serviceName}; resolving...")
                }
                is RemoteDiscoveryEvent.ResolveFailed -> {
                    uiState = uiState.copy(statusText = "Found TV, but could not resolve address: ${event.errorCode}")
                }
                is RemoteDiscoveryEvent.Resolved -> handleResolvedDiscovery(event)
                RemoteDiscoveryEvent.Lost -> {
                    uiState = uiState.copy(
                        statusText = "TV disappeared from discovery. If playback still works, you can keep using the saved address.",
                    )
                }
                is RemoteDiscoveryEvent.Failed -> {
                    uiState = uiState.copy(
                        statusText = "Discovery failed: ${event.errorCode}. Keeping saved/manual TV address and retrying shortly.",
                    )
                    stopDiscovery()
                    handler.postDelayed(discoveryRetry, DISCOVERY_RETRY_MS)
                }
                is RemoteDiscoveryEvent.Unavailable -> {
                    uiState = uiState.copy(
                        statusText = "Discovery unavailable. Enter the TV IP manually. ${event.message}",
                    )
                    handler.postDelayed(discoveryRetry, DISCOVERY_RETRY_MS)
                }
            }
        }
    }

    private fun handleResolvedDiscovery(event: RemoteDiscoveryEvent.Resolved) {
        if (uiState.host.isBlank() || !userEditedTarget) {
            uiState = uiState.copy(
                host = event.host,
                port = event.port.toString(),
                targetSource = RemoteTargetSource.DISCOVERED,
                statusText = "Connected to ${event.serviceName} at ${event.host}:${event.port}",
            )
            persistRemoteTarget()
            updateRemoteNotification()
        } else {
            uiState = uiState.copy(
                statusText = "Found ${event.serviceName} at ${event.host}:${event.port}. Keeping your manual TV address.",
            )
        }
    }

    private companion object {
        const val STATE_POLL_MS = 1000L
        const val DISCOVERY_RETRY_MS = 10_000L
    }
}
