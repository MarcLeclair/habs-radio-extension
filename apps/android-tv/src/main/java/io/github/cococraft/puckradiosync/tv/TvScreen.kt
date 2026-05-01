package io.github.cococraft.puckradiosync.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import io.github.cococraft.puckradiosync.androidcommonui.HrsBufferDelayBar
import io.github.cococraft.puckradiosync.androidcommonui.HrsColors
import io.github.cococraft.puckradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.cococraft.puckradiosync.shared.HRS_PRIVACY_DETAILS
import io.github.cococraft.puckradiosync.shared.radioStateFromJson
import kotlin.math.roundToInt

@Composable
fun TvApp(
    statusBody: String,
    diagnosticsEnabled: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onSecretTap: () -> Unit,
) {
    TvTheme {
        var showPrivacy by remember { mutableStateOf(false) }
        val playFocusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 64.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Header(
                    onSecretTap = onSecretTap,
                    onPrivacyClick = { showPrivacy = true },
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.intro_text, HRS_DEFAULT_CONTROL_PORT),
                    color = TvMutedText,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                    text = stringResource(R.string.onboarding_text),
                    color = TvSecondaryText,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
                StatusPanel(statusBody = statusBody, diagnosticsEnabled = diagnosticsEnabled)
                Spacer(Modifier.height(24.dp))
                Controls(
                    playFocusRequester = playFocusRequester,
                    onPlay = onPlay,
                    onPause = onPause,
                    onStop = onStop,
                    onRefresh = onRefresh,
                )
            }
        }

        if (showPrivacy) {
            PrivacyDialog(onDismiss = { showPrivacy = false })
        }

        LaunchedEffect(Unit) {
            playFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TvSurface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.dialog_privacy_title),
                color = TvText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = HRS_PRIVACY_DETAILS,
                color = TvSecondaryText,
                fontSize = 16.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvControlButton(
                    text = stringResource(R.string.dialog_ok),
                    onClick = onDismiss,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(
    onSecretTap: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    var titleFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .border(
                    width = if (titleFocused) 3.dp else 0.dp,
                    color = if (titleFocused) TvFocusRing else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .clip(RoundedCornerShape(8.dp))
                .background(if (titleFocused) TvFocusedTitleBackground else TvTitleBackground)
                .onFocusChanged { titleFocused = it.isFocused }
                .clickable(onClick = onSecretTap)
                .focusable()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(16.dp))
        TvControlButton(
            text = "?",
            onClick = onPrivacyClick,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            fontSize = 22,
        )
    }
}

@Composable
private fun StatusPanel(statusBody: String, diagnosticsEnabled: Boolean) {
    val status = remember(statusBody, diagnosticsEnabled) {
        runCatching { radioStateFromJson(statusBody) }.getOrNull()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TvSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (status == null) {
                    statusBody
                } else {
                    buildString {
                        append(status.status.name)
                        append(" | ")
                        append(if (status.playing) "Playing" else "Not playing")
                        append(" | Vol ")
                        append(status.volumePercent)
                        append("%")
                        append("\nDelay ")
                        append(status.delaySeconds.roundToInt())
                        append("s")
                        append("\nBuffer: ")
                        append(status.delayAvailableSeconds.roundToInt())
                        append("/60s")
                        if (status.delaySeconds > status.delayAvailableSeconds + 0.05) append(" loading")
                        if (diagnosticsEnabled) {
                            append("\nPCM ")
                            append(status.audioBytesWritten)
                            append(" bytes @ ")
                            append(status.audioBytesPerSecond)
                            append(" B/s")
                        }
                        status.error?.takeIf { it.isNotBlank() }?.let {
                            append("\n")
                            append(it)
                        }
                    }
                },
                color = TvText,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        HrsBufferDelayBar(
            bufferFraction = ((status?.delayAvailableSeconds ?: 0.0) / 60.0).toFloat().coerceIn(0f, 1f),
            delayFraction = ((status?.delaySeconds ?: 0.0) / 60.0).toFloat().coerceIn(0f, 1f),
            heightDp = 18,
        )
    }
}

@Composable
private fun Controls(
    playFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        TvControlButton(
            text = stringResource(R.string.button_play_radio),
            onClick = onPlay,
            modifier = Modifier
                .width(180.dp)
                .focusRequester(playFocusRequester),
        )
        TvControlButton(
            text = stringResource(R.string.button_pause),
            onClick = onPause,
            modifier = Modifier.width(180.dp),
        )
        TvControlButton(
            text = stringResource(R.string.button_stop),
            onClick = onStop,
            modifier = Modifier.width(180.dp),
        )
        TvControlButton(
            text = stringResource(R.string.button_refresh_status),
            onClick = onRefresh,
            modifier = Modifier.width(210.dp),
        )
    }
}

@Composable
private fun TvControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    fontSize: Int = 16,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) TvFocusRing else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(if (focused) TvFocusedButton else TvButton)
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = TvBackground,
            surface = TvSurface,
            primary = TvBuffer,
            secondary = TvDelay,
            onBackground = TvText,
            onSurface = TvText,
        ),
        content = content,
    )
}

private val TvBackground = HrsColors.Background
private val TvSurface = HrsColors.TvSurface
private val TvTitleBackground = HrsColors.TvTitleBackground
private val TvFocusedTitleBackground = HrsColors.Delay
private val TvText = HrsColors.Text
private val TvSecondaryText = HrsColors.SecondaryText
private val TvMutedText = HrsColors.MutedText
private val TvButton = HrsColors.TvButton
private val TvFocusedButton = HrsColors.Button
private val TvFocusRing = HrsColors.Primary
private val TvDelay = HrsColors.Delay
private val TvBuffer = HrsColors.Primary
