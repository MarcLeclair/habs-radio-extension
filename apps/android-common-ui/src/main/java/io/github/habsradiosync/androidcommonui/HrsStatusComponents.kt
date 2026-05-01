package io.github.habsradiosync.androidcommonui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HrsMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BasicText(
            text = label,
            style = TextStyle(
                color = HrsColors.MutedText,
                fontSize = 12.sp,
            ),
        )
        BasicText(
            text = value,
            style = TextStyle(
                color = HrsColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
fun HrsBufferDelayBar(
    bufferFraction: Float,
    delayFraction: Float,
    modifier: Modifier = Modifier,
    heightDp: Int = 14,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape((heightDp / 2).dp))
            .background(HrsColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferFraction.coerceIn(0f, 1f))
                .height(heightDp.dp)
                .background(HrsColors.Primary),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(delayFraction.coerceIn(0f, 1f))
                .height(heightDp.dp)
                .background(HrsColors.Delay),
        )
    }
}
