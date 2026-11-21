package com.donut.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.ui.theme.DonutTheme

enum class ChipStyle {
    OPEN,        // free spots: green container
    YOU,         // the signed-in user is in it: ink
    BOOKED,      // every spot filled: neutral
    DONE,        // completed: outline only
    CANCELLED,   // red container
    TOURNAMENT,  // ball-yellow container
    UP,          // rating went up
    DOWN,        // rating went down
    OUTLINE,     // a plain fact, like a matchmaking criterion
    LIVE,        // happening right now: the live dot and green text, no fill
}

/**
 * A 24 dp label for a state, like "1 spot left", "Booked" or "+14"
 * @param text what the chip says
 * @param style its colours
 * @param icon an optional 14 dp icon before the text, e.g. R.drawable.ic_star_filled
 */
@Composable
fun StatusChip(
    text: String,
    style: ChipStyle,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val (container, content) = chipColors(style)
    val border = when (style) {
        ChipStyle.DONE, ChipStyle.OUTLINE -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    Surface(
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            // A live chip has no fill, so it lines up with the text around it, like the design's live label
            modifier = Modifier.padding(horizontal = if (style == ChipStyle.LIVE) 0.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (style == ChipStyle.LIVE) {
                LiveDot(dotSize = 6.dp)
            } else if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun chipColors(style: ChipStyle): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val donut = DonutTheme.colors
    return when (style) {
        ChipStyle.OPEN -> colors.secondaryContainer to colors.onSecondaryContainer
        ChipStyle.YOU -> colors.primary to colors.onPrimary
        ChipStyle.BOOKED -> colors.surfaceContainerHigh to colors.onSurface
        ChipStyle.DONE -> Color.Transparent to colors.onSurfaceVariant
        ChipStyle.CANCELLED -> colors.errorContainer to colors.onErrorContainer
        ChipStyle.TOURNAMENT -> colors.tertiaryContainer to colors.onTertiaryContainer
        ChipStyle.UP -> donut.winContainer to donut.onWinContainer
        ChipStyle.DOWN -> donut.lossContainer to donut.onLossContainer
        ChipStyle.OUTLINE -> Color.Transparent to colors.onSurface
        ChipStyle.LIVE -> Color.Transparent to colors.secondary
    }
}

/** How a game's status is drawn as a chip */
val GameStatus.chipStyle: ChipStyle
    get() = when (this) {
        GameStatus.OPEN -> ChipStyle.OPEN
        GameStatus.BOOKED -> ChipStyle.BOOKED
        // Being played right now: the live dot, like a friend who is playing
        GameStatus.IN_PROGRESS -> ChipStyle.LIVE
        GameStatus.COMPLETED -> ChipStyle.DONE
        GameStatus.CANCELLED -> ChipStyle.CANCELLED
    }
