package com.example.cpen321application.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerBorderWidth
import com.example.cpen321application.core.ui.theme.sticker

// The thumb slides on spring(0.6, 500)
private const val SLIDE_DAMPING = 0.6f
private const val SLIDE_STIFFNESS = 500f
private const val FADE_MS = 150
private const val DISABLED_ALPHA = 0.38f
private val TRACK_INSET = 4.dp
private val ITEM_HEIGHT = 40.dp

/**
 * A pill-shaped single choice, e.g. Singles · Doubles · Either, also used as the tabs of Games
 * and Profile. The selected option sits on a white mini sticker (ink border, 2 dp shadow) that
 * slides between options. Each option is a radio button (or a tab) for TalkBack.
 * @param options the choices, in order
 * @param selected the chosen one
 * @param label the text of an option
 * @param onSelect called with the option the user tapped
 * @param enabled false greys it out and ignores taps
 * @param role what TalkBack calls each option: Role.RadioButton, or Role.Tab for tabs
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    val colors = MaterialTheme.colorScheme
    val donut = DonutTheme.colors
    val reducedMotion = rememberReducedMotion()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(colors.surfaceContainerHigh, CircleShape)
            .border(StickerBorderWidth, colors.outlineVariant, CircleShape)
            .padding(TRACK_INSET),
    ) {
        val itemWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = itemWidth * options.indexOf(selected).coerceAtLeast(0),
            animationSpec = if (reducedMotion) snap() else spring(SLIDE_DAMPING, SLIDE_STIFFNESS),
            label = "thumb",
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .width(itemWidth)
                .height(ITEM_HEIGHT)
                .sticker(CircleShape, donut.stickerBorder, donut.stickerShadow, offset = 2.dp)
                .background(colors.surfaceContainerLowest, CircleShape),
        )
        Row(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                SegmentedOption(
                    text = label(option),
                    isSelected = option == selected,
                    enabled = enabled,
                    role = role,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SegmentedOption(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    role: Role,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = tween(FADE_MS),
        label = "text",
    )
    Box(
        modifier = modifier
            .height(ITEM_HEIGHT)
            .selectable(selected = isSelected, enabled = enabled, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
