package com.example.cpen321application.core.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.theme.DonutTheme
import kotlinx.coroutines.delay

private const val SPIN_MS = 1100

// How long a success message stays before hiding itself, like a snackbar
private const val SUCCESS_MESSAGE_MS = 4_000L
private const val FULL_TURN = 360f
// The moving arc covers a quarter of the ring
private const val ARC_SWEEP = 90f

/**
 * @return true when the user turned animations off (Developer options or Accessibility → Remove animations)
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * A thin ring with a clay arc that turns once every 1.1 s. With animations off it stays still.
 * @param size the ring's diameter
 */
@Composable
fun RingSpinner(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val arc = MaterialTheme.colorScheme.primary
    val reducedMotion = rememberReducedMotion()
    val rotation = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "spinner")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN,
            animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
        angle
    }
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val width = 2.dp.toPx()
        drawCircle(color = track, radius = this.size.minDimension / 2 - width / 2, style = Stroke(width))
        drawArc(
            color = arc,
            startAngle = rotation - FULL_TURN / 4,
            sweepAngle = ARC_SWEEP,
            useCenter = false,
            topLeft = Offset(width / 2, width / 2),
            size = Size(this.size.width - width, this.size.height - width),
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }
}

/**
 * A MessageBanner floating over the top of the screen: it slides down when a message arrives and back up when
 * it is dismissed or times out, without moving anything under it. Put it last in a Box over the screen
 * @param message what happened, or null to hide it
 * @param isError red, and keeps the message up until dismissed
 * @param onDismiss hides the message
 */
@Composable
fun FloatingMessage(message: String?, isError: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // The last message shown, so the banner keeps its text while it slides away after message becomes null
    var shown by remember { mutableStateOf((message ?: "") to isError) }
    if (message != null) {
        shown = message to isError
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically { height -> -height } + fadeIn(),
        exit = slideOutVertically { height -> -height } + fadeOut(),
    ) {
        MessageBanner(message = shown.first, isError = shown.second, onDismiss = onDismiss)
    }
}

/**
 * A soft pill message, e.g. "Friend request sent!": grass for good news, red for errors, with a
 * close button. It is a sticker, since it floats over the screen. A success hides itself after a
 * few seconds, like a snackbar; an error stays until dismissed, so it isn't missed
 * @param message what happened
 * @param isError red, leaves out the success check, and keeps the message up
 * @param onDismiss hides the message
 */
@Composable
fun MessageBanner(message: String, isError: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // The latest onDismiss, without restarting the wait when the caller passes a new lambda
    val dismiss by rememberUpdatedState(onDismiss)
    // Keyed on the message, so a new success gets its full time on screen
    LaunchedEffect(message, isError) {
        if (!isError) {
            delay(SUCCESS_MESSAGE_MS)
            dismiss()
        }
    }

    val colors = MaterialTheme.colorScheme
    StickerSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isError) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Errors say what went wrong in the text; successes get a check
            if (!isError) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_filled),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Dismiss")
            }
        }
    }
}

/**
 * The Clubhouse tennis ball: ball yellow with an ink outline and white seams. It bounces while
 * the app looks for players (1 s, ease in and out); with animations off it stays still.
 * @param size its diameter
 * @param bouncing whether it bounces
 */
@Composable
fun TennisBall(modifier: Modifier = Modifier, size: Dp = 30.dp, bouncing: Boolean = false) {
    val ball = MaterialTheme.colorScheme.tertiary
    val ink = DonutTheme.colors.stickerBorder
    val reducedMotion = rememberReducedMotion()
    val lift = if (bouncing && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "bounce")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BOUNCE_MS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "lift",
        )
        value
    } else {
        0f
    }
    Canvas(modifier = modifier.size(size)) {
        // Drawn on a 34 x 34 grid, as in the design
        val unit = this.size.minDimension / BALL_GRID
        translate(top = -lift * BOUNCE_HEIGHT * unit) {
            drawCircle(color = ball, radius = BALL_RADIUS * unit)
            drawCircle(color = ink, radius = BALL_RADIUS * unit, style = Stroke(width = 2 * unit))
            val seam = Path().apply {
                moveTo(6 * unit, 8 * unit)
                cubicTo(12 * unit, 12 * unit, 12 * unit, 22 * unit, 6 * unit, 26 * unit)
                moveTo(28 * unit, 8 * unit)
                cubicTo(22 * unit, 12 * unit, 22 * unit, 22 * unit, 28 * unit, 26 * unit)
            }
            drawPath(seam, color = Color.White, style = Stroke(width = 2 * unit, cap = StrokeCap.Round))
        }
    }
}

private const val BOUNCE_MS = 1000
private const val BALL_GRID = 34f
private const val BALL_RADIUS = 15f
// How high the ball bounces, in grid units
private const val BOUNCE_HEIGHT = 5f
