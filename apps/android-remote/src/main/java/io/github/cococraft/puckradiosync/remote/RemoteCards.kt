package io.github.cococraft.puckradiosync.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cococraft.puckradiosync.androidcommonui.HrsBufferDelayBar
import io.github.cococraft.puckradiosync.androidcommonui.HrsMetric
import io.github.cococraft.puckradiosync.shared.HRS_PRIVACY_DETAILS
import io.github.cococraft.puckradiosync.shared.HRS_PRIVACY_SUMMARY
import io.github.cococraft.puckradiosync.shared.PlaybackStatus
import io.github.cococraft.puckradiosync.shared.RemotePaths

@Composable
fun Header(
    statusText: String,
    playbackStatus: PlaybackStatus,
    onSecretTap: () -> Unit,
    onPrivacyClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Puck Radio Remote",
                modifier = Modifier.clickable(onClick = onSecretTap),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = playbackStatus.label,
                    color = playbackStatus.pillColor,
                    onClick = onStatusClick,
                )
                ActionButton(
                    text = "?",
                    modifier = Modifier.size(40.dp),
                    onClick = onPrivacyClick,
                )
            }
        }
        Text(
            text = statusText,
            color = RemoteMutedText,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun HelpButtonRow(
    onFirstRunClick: () -> Unit,
    onPermissionsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionButton(
            text = "First run",
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            onClick = onFirstRunClick,
        )
        ActionButton(
            text = "Permissions",
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            onClick = onPermissionsClick,
        )
    }
}

@Composable
fun ConnectionCard(
    host: String,
    port: String,
    targetSource: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    RemoteCard(title = "Connection") {
        Text(
            text = if (targetSource == RemoteTargetSource.DISCOVERED) "Using discovered TV" else "Using manually selected TV",
            color = RemoteMutedText,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("TV IP address") },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            ActionButton(
                text = "Test",
                modifier = Modifier
                    .height(56.dp)
                    .width(112.dp),
                onClick = onTest,
            )
        }
    }
}

@Composable
fun TransportCard(onCommand: (String) -> Unit) {
    RemoteCard(title = "Playback") {
        ButtonGrid(
            actions = listOf(
                "Play" to RemotePaths.PLAY,
                "Pause" to RemotePaths.PAUSE,
                "Stop" to RemotePaths.STOP,
                "Reload" to RemotePaths.RELOAD,
            ),
            onCommand = onCommand,
        )
    }
}

@Composable
fun SyncCard(
    delaySeconds: Int,
    availableBufferSeconds: Int,
    isLoadingDelay: Boolean,
    onCommand: (String) -> Unit,
) {
    RemoteCard(title = "Sync") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            HrsMetric("Delay", "${delaySeconds}s")
            HrsMetric("Ready", "${availableBufferSeconds}/60s")
        }
        Spacer(Modifier.height(12.dp))
        HrsBufferDelayBar(
            bufferFraction = (availableBufferSeconds / 60f).coerceIn(0f, 1f),
            delayFraction = (delaySeconds / 60f).coerceIn(0f, 1f),
        )
        if (isLoadingDelay) {
            Spacer(Modifier.height(8.dp))
            Text("Loading selected delay", color = RemoteWarning, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        CompactButtonRow(
            actions = listOf(
                RemoteAction("-10s", RemotePaths.nudge(-10.0), enabled = delaySeconds >= 10),
                RemoteAction("-5s", RemotePaths.nudge(-5.0), enabled = delaySeconds >= 5),
                RemoteAction("-1s", RemotePaths.nudge(-1.0), enabled = delaySeconds >= 1),
                RemoteAction("+1s", RemotePaths.nudge(1.0), enabled = delaySeconds <= 59),
                RemoteAction("+5s", RemotePaths.nudge(5.0), enabled = delaySeconds <= 55),
                RemoteAction("+10s", RemotePaths.nudge(10.0), enabled = delaySeconds <= 50),
                RemoteAction("Reset", RemotePaths.delay(0.0), enabled = delaySeconds > 0),
            ),
            onCommand = onCommand,
        )
    }
}

@Composable
fun VolumeCard(volumePercent: Int, onCommand: (String) -> Unit) {
    RemoteCard(title = "Volume") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            HrsMetric("Current", "$volumePercent%")
        }
        Spacer(Modifier.height(12.dp))
        ButtonGrid(
            actions = listOf(
                "-10%" to RemotePaths.volumeNudge(-10),
                "+10%" to RemotePaths.volumeNudge(10),
                "100%" to RemotePaths.volume(100),
                "150%" to RemotePaths.volume(150),
                "200%" to RemotePaths.volume(200),
                "300%" to RemotePaths.volume(300),
                "400%" to RemotePaths.volume(400),
            ),
            onCommand = onCommand,
        )
    }
}

@Composable
fun DiagnosticsCard(audioBytesWritten: Long, audioBytesPerSecond: Int) {
    RemoteCard(title = "Diagnostics") {
        Text(
            text = "PCM $audioBytesWritten bytes @ $audioBytesPerSecond B/s",
            color = RemoteMutedText,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(HRS_PRIVACY_SUMMARY, color = RemoteText, fontSize = 14.sp)
                Text(HRS_PRIVACY_DETAILS, color = RemoteMutedText, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Composable
fun FirstRunDialog(onDismiss: () -> Unit) {
    InfoDialog(
        title = "First run",
        body = "Install and open Puck Radio Sync on your TV first. Then open this remote on your phone. It will look for the TV on the same Wi-Fi. If discovery does not find it, enter the TV IP address manually.",
        onDismiss = onDismiss,
    )
}

@Composable
fun PermissionsDialog(onDismiss: () -> Unit) {
    InfoDialog(
        title = "Permissions",
        body = "Nearby Wi-Fi devices is used only to discover the TV app on your local network. Notifications are used only for quick remote controls. The app does not use accounts, ads, analytics, or tracking.",
        onDismiss = onDismiss,
    )
}

@Composable
fun StatusHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback status") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Idle: the TV app is ready, but radio playback is stopped.", color = RemoteText, fontSize = 14.sp)
                Text("Buffering: the TV is loading the radio stream.", color = RemoteText, fontSize = 14.sp)
                Text("Playing: audio is currently playing.", color = RemoteText, fontSize = 14.sp)
                Text("Paused: playback is paused.", color = RemoteText, fontSize = 14.sp)
                Text("Error: playback or the remote connection hit a problem.", color = RemoteText, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Composable
private fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                color = RemoteText,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

private val PlaybackStatus.label: String
    get() = when (this) {
        PlaybackStatus.Idle -> "Idle"
        PlaybackStatus.Buffering -> "Buffering"
        PlaybackStatus.Playing -> "Playing"
        PlaybackStatus.Paused -> "Paused"
        PlaybackStatus.Error -> "Error"
    }

private val PlaybackStatus.pillColor: androidx.compose.ui.graphics.Color
    get() = when (this) {
        PlaybackStatus.Error -> RemoteError
        PlaybackStatus.Idle -> RemoteIdle
        PlaybackStatus.Buffering,
        PlaybackStatus.Playing,
        PlaybackStatus.Paused -> RemoteActive
    }
