package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PrimaryPink
import com.example.ui.theme.SecondaryPink
import kotlin.math.sin

@Composable
fun VoiceWaveAnimation(
    rmsLevel: Float, // 0f to 1f
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val barCount = 36
            val barSpacing = width / barCount
            val ampMultiplier = (rmsLevel * 24f + 8f).coerceIn(4f, 26f)

            for (i in 0 until barCount) {
                val x = i * barSpacing + barSpacing / 2f
                val waveHeight = (sin(i * 0.35f + phase) * ampMultiplier).coerceAtLeast(3f)

                val barColor = if (i % 2 == 0) PrimaryPink else SecondaryPink

                drawLine(
                    color = barColor.copy(alpha = 0.85f),
                    start = Offset(x, centerY - waveHeight),
                    end = Offset(x, centerY + waveHeight),
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun MicPulseRing(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isListening) return

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Canvas(modifier = modifier.size(80.dp)) {
        drawCircle(
            color = PrimaryPink.copy(alpha = pulseAlpha),
            radius = (size.minDimension / 2f) * pulseScale,
            center = center
        )
    }
}
