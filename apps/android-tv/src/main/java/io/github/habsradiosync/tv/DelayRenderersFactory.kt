package io.github.habsradiosync.tv

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
class DelayRenderersFactory(
    context: Context,
    private val delayProcessor: DelayAudioProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(delayProcessor))
            .build()
    }
}
