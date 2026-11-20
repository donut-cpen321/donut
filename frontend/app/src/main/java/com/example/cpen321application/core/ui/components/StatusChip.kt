package com.example.cpen321application.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerBorderWidth

enum class ChipStyle {
    OPEN,        // free spots: grass container
    YOU,         // the signed-in user is in it: clay
    BOOKED,      // every spot filled: info blue container
    DONE,        // completed: neutral
    CANCELLED,   // red container
    TOURNAMENT,  // ball yellow with an ink border
    DATE,        // when a game starts: peach container
    UP,          // rating went up
    DOWN,        // rating went down
    OUTLINE,     // a plain fact on white with an ink border, e.g. the rank on Profile
    LIVE,        // happening right now: the live dot and clay text, no fill
}

/**
 * A 28 dp pill for a state, like "1 spot left", "Booked" or "Today · 6:30 PM"
 * @param text what the chip says
 * @param style its colours
 * @param icon an optional 16 dp filled icon before the text, e.g. R.drawable.ic_emoji_events_filled
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
        ChipStyle.TOURNAMENT, ChipStyle.OUTLINE -> BorderStroke(StickerBorderWidth, DonutTheme.colors.stickerBorder)
        else -> null
    }
    Surface(
        modifier = modifier.height(28.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            // A live chip has no fill, so it lines up with the text around it, like the design's live label
            modifier = when {
                style == ChipStyle.LIVE -> Modifier
                icon == null -> Modifier.padding(horizontal = 11.dp)
                else -> Modifier.padding(start = 8.dp, end = 11.dp)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (style == ChipStyle.LIVE) {
                LiveDot(dotSize = 6.dp)
            } else if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/**
 * The "Donut set!" badge for a won set that ended 6–0, with the donut mark
 */
@Composable
fun DonutSetChip(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(24.dp),
        shape = CircleShape,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DonutMark(size = 18.dp)
            Text(text = "Donut set!", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        ChipStyle.BOOKED -> donut.infoContainer to donut.onInfoContainer
        ChipStyle.DONE -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        ChipStyle.CANCELLED -> colors.errorContainer to colors.onErrorContainer
        ChipStyle.TOURNAMENT -> colors.tertiary to colors.onTertiary
        ChipStyle.DATE -> colors.primaryContainer to colors.onPrimaryContainer
        ChipStyle.UP -> donut.winContainer to donut.onWinContainer
        ChipStyle.DOWN -> donut.lossContainer to donut.onLossContainer
        ChipStyle.OUTLINE -> colors.surfaceContainerLowest to colors.onSurface
        ChipStyle.LIVE -> Color.Transparent to donut.live
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
