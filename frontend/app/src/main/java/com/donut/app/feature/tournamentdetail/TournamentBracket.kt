package com.donut.app.feature.tournamentdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
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

/**
 * The bracket, one column per round, scrolling sideways on narrow screens
 * @param columns the quarterfinals, semifinals and final, from bracketColumns
 * @param onMatchClick called with the game of a tapped match. Matches without a game can't be tapped
 */
@Composable
fun TournamentBracket(columns: List<BracketColumn>, onMatchClick: (gameId: String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        columns.forEach { column ->
            RoundColumn(column = column, onMatchClick = onMatchClick)
        }
    }
}

@Composable
private fun RoundColumn(column: BracketColumn, onMatchClick: (gameId: String) -> Unit) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = column.title, style = MaterialTheme.typography.titleSmall)
        // Spread the matches out so each one sits between the two that feed it
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceAround
        ) {
            column.matches.forEach { match ->
                MatchCard(match = match, onMatchClick = onMatchClick)
            }
        }
    }
}

// A hairline card per match; it opens the match's game once both teams are known
@Composable
private fun MatchCard(match: BracketMatch, onMatchClick: (gameId: String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val decided = match.team1.isWinner || match.team2.isWinner
    Surface(
        onClick = { match.gameId?.let(onMatchClick) },
        enabled = match.gameId != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            SlotRow(slot = match.team1, matchHasWinner = decided)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
            SlotRow(slot = match.team2, matchHasWinner = decided)
        }
    }
}

@Composable
private fun SlotRow(slot: BracketSlot, matchHasWinner: Boolean) {
    // Once the match has a winner, the loser is muted
    val colors = MaterialTheme.colorScheme
    val color = if (matchHasWinner && !slot.isWinner) colors.onSurfaceVariant else colors.onSurface
    val numbers = DonutTextStyles.numericSmall.copy(fontWeight = if (slot.isWinner) FontWeight.SemiBold else FontWeight.Medium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = slot.seed?.toString().orEmpty(),
            style = DonutTextStyles.numericSmall.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = slot.name,
            style = if (slot.isWinner) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        slot.scores.forEach { score ->
            Text(
                text = score.toString(),
                style = numbers,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.width(14.dp)
            )
        }
    }
}
