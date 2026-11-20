package com.example.cpen321application.core.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.ui.theme.DonutTheme

// The halo pulses like the design's live dot (animation: pulse 1.6s ease-out infinite): it grows from the dot's
// size to twice it while fading out, then starts again
private const val PULSE_MS = 1600
private const val HALO_START_ALPHA = 0.35f

/**
 * A dot in the live colour (clay) with a pulsing halo, for something happening right now, e.g. a match
 * found or a friend playing.
 * It always takes twice the dot's size, so the pulse never moves what is around it
 * @param dotSize the dot's diameter; the halo grows to twice it
 */
@Composable
fun LiveDot(modifier: Modifier = Modifier, dotSize: Dp = 8.dp) {
    val live = DonutTheme.colors.live
    val pulse by rememberInfiniteTransition(label = "live dot").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearOutSlowInEasing)),
        label = "halo",
    )
    Box(
        modifier = modifier
            .size(dotSize * 2)
            .drawBehind {
                // From the dot's edge out to the box's edge (twice as far), fading to nothing
                val dotRadius = size.minDimension / 2 / 2
                val radius = dotRadius * (1 + pulse)
                drawCircle(color = live.copy(alpha = HALO_START_ALPHA * (1 - pulse)), radius = radius)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(dotSize).background(live, CircleShape))
    }
}

/**
 * The live dot, then the state in small caps, e.g. "MATCH FOUND" or "PLAYING NOW"
 */
@Composable
fun LiveLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiveDot()
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = DonutTheme.colors.live,
        )
    }
}

/**
 * A smaller live dot and a short state in the list's second-line style, e.g. "Playing", for a row
 */
@Composable
fun LiveStatusText(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LiveDot(dotSize = 6.dp)
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = DonutTheme.colors.live)
    }
}
