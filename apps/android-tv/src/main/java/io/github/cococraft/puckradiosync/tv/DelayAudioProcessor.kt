package io.github.cococraft.puckradiosync.tv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.roundToInt

@UnstableApi
class DelayAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var requestedDelaySeconds: Double = 0.0
    @Volatile
    private var gain = 1.0

    private var ring = ByteArray(0)
    private var writePosition = 0
    @Volatile
    private var writtenBytes = 0L
    @Volatile
    private var bytesPerFrame = 0
    @Volatile
    private var bytesPerSecond = 0
    private var lastInputLogAtBytes = 0L

    fun setDelaySeconds(seconds: Double) {
        val nextDelay = seconds.coerceIn(0.0, MAX_DELAY_SECONDS)
        debugLog("setDelay requested=$nextDelay available=${availableBufferedSeconds()} written=$writtenBytes byteRate=$bytesPerSecond")
        requestedDelaySeconds = nextDelay
    }

    fun setGain(nextGain: Double) {
        gain = nextGain.coerceIn(0.0, MAX_GAIN)
        debugLog("setGain gain=$gain")
    }

    fun clearBuffer() {
        clearBufferedAudio()
        debugLog("clear")
    }

    fun snapshot(): DelayBufferSnapshot {
        val requested = requestedDelaySeconds
        val available = availableBufferedSeconds()
        val buffered = available.coerceAtMost(requested)
        return DelayBufferSnapshot(
            requestedSeconds = requested,
            bufferedSeconds = buffered.coerceIn(0.0, requested),
            availableSeconds = available,
            buffering = available + 0.05 < requested,
            audioBytesWritten = writtenBytes,
            audioBytesPerSecond = bytesPerSecond,
        )
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        bytesPerFrame = inputAudioFormat.channelCount * BYTES_PER_SAMPLE_16_BIT
        bytesPerSecond = inputAudioFormat.sampleRate * bytesPerFrame
        ring = ByteArray(bytesPerSecond * MAX_DELAY_SECONDS.toInt() + bytesPerFrame)
        writePosition = 0
        writtenBytes = 0
        lastInputLogAtBytes = 0
        debugLog("configure sampleRate=${inputAudioFormat.sampleRate} channels=${inputAudioFormat.channelCount} bytesPerFrame=$bytesPerFrame bytesPerSecond=$bytesPerSecond")
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(inputSize)
        val delayBytes = effectiveDelayBytes()
        val buffering = availableBufferedSeconds() + 0.001 < requestedDelaySeconds

        while (inputBuffer.hasRemaining()) {
            val sampleStart = writePosition
            val low = inputBuffer.get()
            val high = inputBuffer.get()

            val delayedLow: Byte
            val delayedHigh: Byte
            if (delayBytes == 0) {
                delayedLow = low
                delayedHigh = high
            } else if (buffering) {
                delayedLow = 0
                delayedHigh = 0
            } else {
                val read = readPosition(delayBytes)
                delayedLow = ring[read]
                delayedHigh = ring[(read + 1) % ring.size]
            }

            ring[sampleStart] = low
            writePosition = (writePosition + 1) % ring.size
            ring[writePosition] = high
            writePosition = (writePosition + 1) % ring.size
            writtenBytes += BYTES_PER_SAMPLE_16_BIT

            val sample = (delayedHigh.toInt() shl 8) or (delayedLow.toInt() and 0xff)
            val boosted = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.put((boosted and 0xff).toByte())
            outputBuffer.put(((boosted shr 8) and 0xff).toByte())
        }

        if (DebugOptions.enabled && writtenBytes - lastInputLogAtBytes >= bytesPerSecond.coerceAtLeast(1)) {
            lastInputLogAtBytes = writtenBytes
            val snapshot = snapshot()
            debugLog("input written=$writtenBytes requested=${snapshot.requestedSeconds} buffered=${snapshot.bufferedSeconds} buffering=${snapshot.buffering} byteRate=$bytesPerSecond")
        }

        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        clearBufferedAudio()
        debugLog("flush")
    }

    private fun clearBufferedAudio() {
        ring.fill(0)
        writePosition = 0
        writtenBytes = 0
        lastInputLogAtBytes = 0
    }

    override fun onReset() {
        ring = ByteArray(0)
        writePosition = 0
        writtenBytes = 0
        bytesPerFrame = 0
        bytesPerSecond = 0
        lastInputLogAtBytes = 0
        debugLog("reset")
    }

    private fun effectiveDelayBytes(): Int {
        if (bytesPerFrame == 0 || bytesPerSecond == 0) return 0
        val effectiveDelaySeconds = requestedDelaySeconds.coerceAtMost(availableBufferedSeconds())
        val raw = (effectiveDelaySeconds * bytesPerSecond).roundToInt()
        return raw - (raw % bytesPerFrame)
    }

    private fun availableBufferedSeconds(): Double {
        val byteRate = bytesPerSecond
        if (byteRate == 0) return 0.0
        return (writtenBytes.toDouble() / byteRate.toDouble()).coerceIn(0.0, MAX_DELAY_SECONDS)
    }

    private fun readPosition(delayBytes: Int): Int {
        val position = writePosition - delayBytes
        return if (position >= 0) position else position + ring.size
    }

    private fun debugLog(message: String) {
        if (DebugOptions.enabled) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "HRSDelayProcessor"
        const val BYTES_PER_SAMPLE_16_BIT = 2
        const val MAX_DELAY_SECONDS = 60.0
        const val MAX_GAIN = 3.0
    }
}

data class DelayBufferSnapshot(
    val requestedSeconds: Double,
    val bufferedSeconds: Double,
    val availableSeconds: Double,
    val buffering: Boolean,
    val audioBytesWritten: Long,
    val audioBytesPerSecond: Int,
)
