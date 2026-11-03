package com.example.cpen321application.feature.tournamentdetail

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
import androidx.compose.material3.Card
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
        Text(
            text = column.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
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

@Composable
private fun MatchCard(match: BracketMatch, onMatchClick: (gameId: String) -> Unit) {
    Card(
        onClick = { match.gameId?.let(onMatchClick) },
        enabled = match.gameId != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            SlotRow(slot = match.team1)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SlotRow(slot = match.team2)
        }
    }
}

@Composable
private fun SlotRow(slot: BracketSlot) {
    val weight = if (slot.isWinner) FontWeight.Bold else FontWeight.Normal
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = slot.seed?.toString().orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = slot.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        slot.scores.forEach { score ->
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                textAlign = TextAlign.End,
                modifier = Modifier.width(18.dp)
            )
        }
    }
}
