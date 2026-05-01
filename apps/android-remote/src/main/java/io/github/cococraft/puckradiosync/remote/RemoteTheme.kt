package io.github.cococraft.puckradiosync.remote

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import io.github.cococraft.puckradiosync.androidcommonui.HrsColors

val RemoteBackground = HrsColors.Background
val RemoteSurface = HrsColors.Surface
val RemotePrimary = HrsColors.Primary
val RemoteAccent = HrsColors.Accent
val RemoteText = HrsColors.Text
val RemoteMutedText = HrsColors.MutedText
val RemoteButton = HrsColors.Button
val RemoteDisabledButton = HrsColors.DisabledButton
val RemoteDisabledText = HrsColors.DisabledText
val RemoteTrack = HrsColors.Track
val RemoteDelay = HrsColors.Delay
val RemoteWarning = HrsColors.Warning
val RemoteIdle = HrsColors.Idle
val RemoteActive = HrsColors.Active
val RemoteError = HrsColors.Error

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = RemoteBackground,
            surface = RemoteSurface,
            primary = RemotePrimary,
            secondary = RemoteAccent,
            onBackground = RemoteText,
            onSurface = RemoteText,
        ),
        content = content,
    )
}
