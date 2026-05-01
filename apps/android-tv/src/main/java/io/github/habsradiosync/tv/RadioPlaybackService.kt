package io.github.habsradiosync.tv

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.habsradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.habsradiosync.shared.PlaybackStatus
import io.github.habsradiosync.shared.RadioState
import io.github.habsradiosync.shared.RadioStations
import io.github.habsradiosync.shared.RemoteCommand
import io.github.habsradiosync.shared.applyCommand
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class RadioPlaybackService : Service() {
    private var player: ExoPlayer? = null
    private val delayProcessor = DelayAudioProcessor()
    private lateinit var notifications: PlaybackNotificationController
    private lateinit var discovery: TvDiscoveryRegistration
    private var server: RemoteControlServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val delayStateTicker = object : Runnable {
        override fun run() {
            state = state.withDelayBufferSnapshot()
            val snapshot = delayProcessor.snapshot()
            if (DebugOptions.enabled && snapshot.requestedSeconds > 0.0) {
                Log.i(TAG, "tick requested=${snapshot.requestedSeconds} buffered=${snapshot.bufferedSeconds} buffering=${snapshot.buffering} written=${snapshot.audioBytesWritten} byteRate=${snapshot.audioBytesPerSecond}")
            }
            mainHandler.postDelayed(this, DELAY_STATE_TICK_MS)
        }
    }

    @Volatile
    private var state = RadioState()

    override fun onCreate() {
        super.onCreate()
        notifications = PlaybackNotificationController(this)
        discovery = TvDiscoveryRegistration(this)
        notifications.createChannel()
        server = RemoteControlServer(
            getState = { state.withDelayBufferSnapshot() },
            onCommand = { command -> handleCommandOnMain(command) },
            onError = { message ->
                Log.e(TAG, "Control server failed: $message")
                state = state.copy(status = PlaybackStatus.Error, error = "Control server: $message")
                notifications.update("Control server error")
            },
        ).also { it.start() }
        discovery.register()
        mainHandler.post(delayStateTicker)
        startForeground(PlaybackNotificationController.NOTIFICATION_ID, notifications.notification("Ready on port $HRS_DEFAULT_CONTROL_PORT"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> handleCommand(RemoteCommand.Play)
            ACTION_PAUSE -> handleCommand(RemoteCommand.Pause)
            ACTION_STOP -> handleCommand(RemoteCommand.Stop)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayStateTicker)
        discovery.unregister()
        server?.stop()
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun handleCommandOnMain(command: RemoteCommand): RadioState {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return handleCommand(command)
        }

        val latch = CountDownLatch(1)
        val result = AtomicReference<RadioState>()
        mainHandler.post {
            try {
                result.set(handleCommand(command))
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        return result.get() ?: state
    }

    private fun handleCommand(command: RemoteCommand): RadioState {
        state = state.applyCommand(command)
        when (command) {
            RemoteCommand.Play -> play()
            RemoteCommand.Pause -> pause()
            RemoteCommand.Stop -> stop()
            RemoteCommand.Reload -> {
                stopPlayer()
                delayProcessor.clearBuffer()
                state = state.copy(delaySeconds = 0.0, volumePercent = 100)
                play()
            }
            is RemoteCommand.SetStation -> {
                stopPlayer()
                delayProcessor.clearBuffer()
                state = state.copy(delaySeconds = 0.0, volumePercent = 100)
                play()
            }
            is RemoteCommand.SetDelay,
            is RemoteCommand.NudgeDelay -> {
                delayProcessor.setDelaySeconds(state.delaySeconds)
                state = state.withDelayBufferSnapshot()
                notifications.update("Delay ${state.delaySeconds}s")
            }
            is RemoteCommand.SetVolume,
            is RemoteCommand.NudgeVolume -> {
                applyVolume()
                notifications.update("Volume ${state.volumePercent}%")
            }
        }
        state = state.withDelayBufferSnapshot()
        return state
    }

    private fun play() {
        val station = RadioStations.requireById(state.stationId)
        delayProcessor.setDelaySeconds(state.delaySeconds)
        applyVolume()

        player?.let { existingPlayer ->
            existingPlayer.play()
            state = state.copy(
                playing = true,
                status = if (existingPlayer.playbackState == Player.STATE_READY) PlaybackStatus.Playing else PlaybackStatus.Buffering,
                error = null,
            ).withDelayBufferSnapshot()
            notifications.update("Playing ${station.name}")
            return
        }

        state = state.copy(status = PlaybackStatus.Buffering, error = null)
        delayProcessor.setDelaySeconds(state.delaySeconds)
        applyVolume()
        player = ExoPlayer.Builder(this, DelayRenderersFactory(this, delayProcessor))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                state = state.copy(status = PlaybackStatus.Buffering, error = null)
                                notifications.update("Buffering ${station.name}")
                            }
                            Player.STATE_READY -> {
                                state = state.copy(playing = playWhenReady, status = PlaybackStatus.Playing, error = null)
                                notifications.update("Playing ${station.name}")
                            }
                            Player.STATE_ENDED -> {
                                state = state.copy(playing = false, status = PlaybackStatus.Idle)
                                notifications.update("Stopped")
                            }
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error", error)
                        state = state.copy(
                            playing = false,
                            status = PlaybackStatus.Error,
                            error = error.message ?: error.errorCodeName,
                        )
                        notifications.update("Playback error")
                    }
                })
                setMediaItem(MediaItem.fromUri(station.streamUrl))
                prepare()
                play()
            }
        notifications.update("Buffering ${station.name}")
    }

    private fun applyVolume() {
        val percent = state.volumePercent.coerceIn(0, 300)
        val playerVolume = (percent.coerceAtMost(100) / 100f)
        val gain = if (percent <= 100) 1.0 else percent / 100.0
        delayProcessor.setGain(gain)
        player?.volume = playerVolume
    }

    private fun pause() {
        player?.pause()
        state = state.copy(playing = false, status = PlaybackStatus.Paused)
        notifications.update("Paused")
    }

    private fun stop() {
        stopPlayer()
        delayProcessor.clearBuffer()
        state = state.copy(playing = false, status = PlaybackStatus.Idle, error = null)
        notifications.update("Stopped")
    }

    private fun stopPlayer() {
        player?.release()
        player = null
    }

    private fun RadioState.withDelayBufferSnapshot(): RadioState {
        val snapshot = delayProcessor.snapshot()
        return copy(
            delaySeconds = snapshot.requestedSeconds,
            delayBufferedSeconds = snapshot.bufferedSeconds,
            delayAvailableSeconds = snapshot.availableSeconds,
            delayBuffering = snapshot.buffering,
            audioBytesWritten = snapshot.audioBytesWritten,
            audioBytesPerSecond = snapshot.audioBytesPerSecond,
        )
    }

    companion object {
        const val ACTION_PLAY = "io.github.habsradiosync.tv.PLAY"
        const val ACTION_PAUSE = "io.github.habsradiosync.tv.PAUSE"
        const val ACTION_STOP = "io.github.habsradiosync.tv.STOP"

        private const val TAG = "HabsRadioSync"
        private const val DELAY_STATE_TICK_MS = 250L
    }
}
