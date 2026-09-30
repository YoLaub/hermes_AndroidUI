package com.example.hermes.features.workspace.notes.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesPrimary
import com.example.hermes.theme.HermesTertiary

@Composable
fun AudioWaveVisualizer(
    isRecording: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    activeColor: Color = HermesPrimary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    Row(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animOffset = (i * 120)
            val animatedFactor by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + (i % 4) * 80, easing = FastOutSlowInEasing, delayMillis = animOffset % 300),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val heightFraction = if (isRecording) {
                ((rmsLevel * 1.5f + animatedFactor * 0.4f)).coerceIn(0.15f, 1f)
            } else {
                0.15f
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isRecording) activeColor else activeColor.copy(alpha = 0.3f))
            )
        }
    }
}
