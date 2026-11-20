package com.example.cpen321application.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.R
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.StickerBorderWidth

private val SET_CIRCLE = 34.dp
private val ROW_AVATAR = 36.dp
private val DASH = 4.dp

/**
 * One side of a scoreboard
 * @param name e.g. "Alex Rivera" or "You & Priya Nair"
 * @param avatarName whose initials lead the row
 * @param setScores this side's games in each set, in order
 * @param otherSetScores the other side's, to tell which sets this side won
 */
data class ScoreboardSide(
    val name: String,
    val avatarName: String,
    val setScores: List<Int>,
    val otherSetScores: List<Int>,
)

/** Whether this side won more sets than it lost */
private val ScoreboardSide.wonMatch: Boolean
    get() {
        val won = setScores.indices.count { setScores[it] > (otherSetScores.getOrNull(it) ?: Int.MAX_VALUE) }
        val lost = setScores.indices.count { setScores[it] < (otherSetScores.getOrNull(it) ?: Int.MIN_VALUE) }
        return won > lost
    }

/**
 * A sticker score card: a small title, one row per side with each set's games in a circle (the
 * sets a side won are filled grass), a trophy by the winner, and an optional footer such as the
 * rating change. Rows are split by dashed lines.
 * @param title e.g. "Final · Singles", drawn in small caps
 */
@Composable
fun Scoreboard(
    title: String,
    sides: List<ScoreboardSide>,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    DonutCard(modifier = modifier, sticker = true, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        Column {
            SectionLabel(text = title, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            sides.forEachIndexed { index, side ->
                DashedRow(dashed = index > 0) { ScoreboardRow(side = side) }
            }
            if (footer != null) {
                DashedRow(dashed = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { footer() }
                }
            }
        }
    }
}

// A row with a dashed outlineVariant line above it
@Composable
private fun DashedRow(dashed: Boolean, content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier.drawBehind {
            if (dashed) {
                val width = StickerBorderWidth.toPx()
                val dash = DASH.toPx()
                drawLine(
                    color = line,
                    start = Offset(0f, width / 2),
                    end = Offset(size.width, width / 2),
                    strokeWidth = width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
            }
        },
    ) { content() }
}

@Composable
private fun ScoreboardRow(side: ScoreboardSide) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerAvatar(name = side.avatarName, size = ROW_AVATAR)
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = side.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (side.wonMatch) {
                Icon(
                    painter = painterResource(R.drawable.ic_emoji_events_filled),
                    contentDescription = "Winner",
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                )
            }
        }
        side.setScores.forEachIndexed { index, games ->
            val won = games > (side.otherSetScores.getOrNull(index) ?: Int.MAX_VALUE)
            Box(
                modifier = Modifier
                    .size(SET_CIRCLE)
                    .background(if (won) colors.secondary else colors.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = games.toString(),
                    style = DonutTextStyles.numericMedium.copy(fontSize = 15.sp, lineHeight = 15.sp),
                    color = if (won) colors.onSecondary else colors.onSurface,
                )
            }
        }
    }
}

/**
 * The scoreboard's footer for the signed-in user: "Your rating: 1573 → 1587   +14"
 */
@Composable
fun RowScope.RatingChangeFooter(before: Int, after: Int) {
    Text(
        text = "Your rating: $before → $after",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    RatingDelta(change = after - before)
}
