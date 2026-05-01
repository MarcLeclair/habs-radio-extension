package io.github.cococraft.puckradiosync.remote

import io.github.cococraft.puckradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.cococraft.puckradiosync.shared.PlaybackStatus

data class RemoteUiState(
    val host: String = "",
    val port: String = HRS_DEFAULT_CONTROL_PORT.toString(),
    val statusText: String = "Looking for Puck Radio Sync on this Wi-Fi...",
    val isPlaying: Boolean = false,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val delaySeconds: Int = 0,
    val availableBufferSeconds: Int = 0,
    val volumePercent: Int = 100,
    val audioBytesWritten: Long = 0L,
    val audioBytesPerSecond: Int = 0,
    val isLoadingDelay: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
    val targetSource: String = RemoteTargetSource.MANUAL,
)

object RemoteTargetSource {
    const val MANUAL = "manual"
    const val DISCOVERED = "discovered"
}

data class RemoteTarget(
    val host: String,
    val port: String,
    val source: String,
)
