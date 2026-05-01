package io.github.habsradiosync.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.habsradiosync.shared.RemotePaths

@Composable
fun RemoteApp(
    state: RemoteUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onCommand: (String) -> Unit,
    onSecretTap: () -> Unit,
) {
    RemoteTheme {
        var showPrivacy by remember { mutableStateOf(false) }
        var showFirstRun by remember { mutableStateOf(false) }
        var showPermissions by remember { mutableStateOf(false) }
        var showStatusHelp by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 24.dp),
            ) {
                item {
                    Header(
                        statusText = state.statusText,
                        playbackStatus = state.playbackStatus,
                        onSecretTap = onSecretTap,
                        onPrivacyClick = { showPrivacy = true },
                        onStatusClick = { showStatusHelp = true },
                    )
                }
                item {
                    HelpButtonRow(
                        onFirstRunClick = { showFirstRun = true },
                        onPermissionsClick = { showPermissions = true },
                    )
                }
                item {
                    ConnectionCard(
                        host = state.host,
                        port = state.port,
                        targetSource = state.targetSource,
                        onHostChange = onHostChange,
                        onPortChange = onPortChange,
                        onTest = { onCommand(RemotePaths.STATE) },
                    )
                }
                item {
                    SyncCard(
                        delaySeconds = state.delaySeconds,
                        availableBufferSeconds = state.availableBufferSeconds,
                        isLoadingDelay = state.isLoadingDelay,
                        onCommand = onCommand,
                    )
                }
                item {
                    TransportCard(onCommand = onCommand)
                }
                item {
                    VolumeCard(volumePercent = state.volumePercent, onCommand = onCommand)
                }
                if (state.diagnosticsEnabled) {
                    item {
                        DiagnosticsCard(
                            audioBytesWritten = state.audioBytesWritten,
                            audioBytesPerSecond = state.audioBytesPerSecond,
                        )
                    }
                }
            }
        }
        if (showPrivacy) {
            PrivacyDialog(onDismiss = { showPrivacy = false })
        }
        if (showFirstRun) {
            FirstRunDialog(onDismiss = { showFirstRun = false })
        }
        if (showPermissions) {
            PermissionsDialog(onDismiss = { showPermissions = false })
        }
        if (showStatusHelp) {
            StatusHelpDialog(onDismiss = { showStatusHelp = false })
        }
    }
}
