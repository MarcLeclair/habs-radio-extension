package io.github.habsradiosync.shared

const val HRS_SERVICE_TYPE = "_habs-radio-sync._tcp."
const val HRS_DEFAULT_CONTROL_PORT = 8787

object RemotePaths {
    const val STATE = "/state"
    const val PLAY = "/play"
    const val PAUSE = "/pause"
    const val STOP = "/stop"
    const val RELOAD = "/reload"

    fun delay(seconds: Double): String = "/delay?seconds=$seconds"
    fun nudge(seconds: Double): String = "/nudge?seconds=$seconds"
    fun volume(percent: Int): String = "/volume?percent=$percent"
    fun volumeNudge(percent: Int): String = "/volumeNudge?percent=$percent"
    fun station(id: String): String = "/station?id=$id"
}

data class RadioState(
    val stationId: String = RadioStations.chmp.id,
    val playing: Boolean = false,
    val delaySeconds: Double = 0.0,
    val delayBufferedSeconds: Double = 0.0,
    val delayAvailableSeconds: Double = 0.0,
    val delayBuffering: Boolean = false,
    val audioBytesWritten: Long = 0,
    val audioBytesPerSecond: Int = 0,
    val volumePercent: Int = 100,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val error: String? = null,
)

enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
    Error,
}

sealed interface RemoteCommand {
    data object Play : RemoteCommand
    data object Pause : RemoteCommand
    data object Stop : RemoteCommand
    data object Reload : RemoteCommand
    data class SetStation(val stationId: String) : RemoteCommand
    data class SetDelay(val seconds: Double) : RemoteCommand
    data class NudgeDelay(val deltaSeconds: Double) : RemoteCommand
    data class SetVolume(val percent: Int) : RemoteCommand
    data class NudgeVolume(val deltaPercent: Int) : RemoteCommand
}

fun clampDelaySeconds(seconds: Double): Double = seconds.coerceIn(0.0, 60.0)
fun clampVolumePercent(percent: Int): Int = percent.coerceIn(0, 300)

fun RadioState.applyCommand(command: RemoteCommand): RadioState =
    when (command) {
        RemoteCommand.Play -> copy(playing = true, status = PlaybackStatus.Buffering, error = null)
        RemoteCommand.Pause -> copy(playing = false, status = PlaybackStatus.Paused)
        RemoteCommand.Stop -> copy(playing = false, delayBufferedSeconds = 0.0, delayBuffering = false, status = PlaybackStatus.Idle, error = null)
        RemoteCommand.Reload -> copy(status = PlaybackStatus.Buffering, error = null)
        is RemoteCommand.SetStation -> copy(stationId = command.stationId, status = PlaybackStatus.Buffering, error = null)
        is RemoteCommand.SetDelay -> copy(delaySeconds = clampDelaySeconds(command.seconds))
        is RemoteCommand.NudgeDelay -> copy(delaySeconds = clampDelaySeconds(delaySeconds + command.deltaSeconds))
        is RemoteCommand.SetVolume -> copy(volumePercent = clampVolumePercent(command.percent))
        is RemoteCommand.NudgeVolume -> copy(volumePercent = clampVolumePercent(volumePercent + command.deltaPercent))
    }
