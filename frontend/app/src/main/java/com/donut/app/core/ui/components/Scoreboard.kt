package com.donut.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.core.ui.theme.DonutTextStyles

private val SET_COLUMN = 34.dp
private val ROW_AVATAR = 24.dp

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
    val avatarUrl: String? = null,
)

/**
 * A TV-style score card: a header row with a column per set, one row per side, and an optional
 * footer row such as the rating change. Sets a side won are in ink, the rest are muted.
 * @param title e.g. "Final · Singles", drawn in small caps
 */
@Composable
fun Scoreboard(
    title: String,
    sides: List<ScoreboardSide>,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    val sets = sides.maxOfOrNull { it.setScores.size } ?: 0
    DonutCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column {
            ScoreboardHeader(title = title, sets = sets)
            sides.forEach { side ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ScoreboardRow(side = side)
            }
            if (footer != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { footer() }
            }
        }
    }
}

@Composable
private fun ScoreboardHeader(title: String, sets: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        repeat(sets) { index ->
            Text(
                text = "SET ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(SET_COLUMN),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ScoreboardRow(side: ScoreboardSide) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerAvatar(name = side.avatarName, avatarUrl = side.avatarUrl, size = ROW_AVATAR)
        Text(
            text = side.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        side.setScores.forEachIndexed { index, games ->
            val won = games > (side.otherSetScores.getOrNull(index) ?: Int.MAX_VALUE)
            Text(
                text = games.toString(),
                style = DonutTextStyles.numericTitle.copy(fontWeight = if (won) FontWeight.SemiBold else FontWeight.Medium),
                color = if (won) colors.onSurface else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(SET_COLUMN),
            )
        }
    }
}

/**
 * The scoreboard's footer for the signed-in user: "Your rating   1573 → 1587  +14"
 */
@Composable
fun RowScope.RatingChangeFooter(before: Int, after: Int) {
    Text(
        text = "Your rating",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
    )
    Spacer(modifier = Modifier.weight(1f))
    Text(text = "$before → $after", style = DonutTextStyles.numericSmall)
    Spacer(modifier = Modifier.size(8.dp))
    RatingDelta(change = after - before)
}
