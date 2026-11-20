package com.example.cpen321application.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerShadowOffset
import com.example.cpen321application.core.ui.theme.sticker

// Pressed, a sticker sinks 2 dp onto its shadow, on spring(0.55, 400)
private val PRESS_SINK = 2.dp
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 400f

/**
 * How far a sticker has sunk: 0 dp at rest, 2 dp while pressed. With animations off it jumps.
 * @param interactionSource the sticker's clicks
 */
@Composable
fun rememberStickerSink(interactionSource: InteractionSource): State<Dp> {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    return animateDpAsState(
        targetValue = if (pressed) PRESS_SINK else 0.dp,
        animationSpec = if (reducedMotion) snap() else spring(PRESS_DAMPING, PRESS_STIFFNESS),
        label = "sink",
    )
}

/**
 * Anything drawn as a Clubhouse sticker: an ink border and a solid shadow 3 dp below. With an
 * onClick it is a button that sinks onto its shadow while pressed.
 * @param shape its outline, 24 dp corners by default (cards)
 * @param color its fill
 * @param onClick makes it a button, null for one that only shows something
 * @param onClickLabel what TalkBack says a tap does, e.g. "Open the bracket"
 * @param shadowOffset how far the shadow sits below it at rest; 0 dp draws the border only
 * @param border the outline's colour, ink by default
 * @param role what TalkBack calls it when it has an onClick, e.g. Role.RadioButton for an option card
 */
@Composable
fun StickerSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = contentColorFor(color),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    shadowOffset: Dp = StickerShadowOffset,
    border: Color = DonutTheme.colors.stickerBorder,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sink by rememberStickerSink(interactionSource)
    val shadow = DonutTheme.colors.stickerShadow
    val press = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer { translationY = sink.toPx() }
            .sticker(shape, border, shadow, offset = (shadowOffset - sink).coerceAtLeast(0.dp))
            .clip(shape)
            .background(color)
            .then(press),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
