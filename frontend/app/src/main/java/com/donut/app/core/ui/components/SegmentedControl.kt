package com.donut.app.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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

private const val SLIDE_MS = 200
private const val FADE_MS = 150
private const val DISABLED_ALPHA = 0.38f
private val TRACK_INSET = 4.dp
private val ITEM_HEIGHT = 40.dp

/**
 * A pill-shaped single choice, e.g. Singles · Doubles · Either. The selected option sits on a
 * white thumb that slides between options. Each option is a radio button for TalkBack.
 * @param options the choices, in order
 * @param selected the chosen one
 * @param label the text of an option
 * @param onSelect called with the option the user tapped
 * @param enabled false greys it out and ignores taps
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(colors.surfaceContainerHigh, CircleShape)
            .padding(TRACK_INSET),
    ) {
        val itemWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = itemWidth * options.indexOf(selected).coerceAtLeast(0),
            animationSpec = tween(SLIDE_MS),
            label = "thumb",
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .width(itemWidth)
                .height(ITEM_HEIGHT)
                .background(colors.surfaceContainerLowest, CircleShape)
                .border(1.dp, colors.outlineVariant, CircleShape),
        )
        Row(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                SegmentedOption(
                    text = label(option),
                    isSelected = option == selected,
                    enabled = enabled,
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
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
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
