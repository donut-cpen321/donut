package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.ui.forecastText
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.gameTournamentText
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.playerCountText

/**
 * A game in the Games tab: start time, court, format, players and forecast
 * @param game the game
 * @param isPlaying whether the signed-in user plays in it
 * @param onClick opens the game's screen
 */
@Composable
fun GameCard(
    game: Game,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatStartsAt(game.startsAt),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (game.status != GameStatus.OPEN) {
                    Text(
                        text = game.status.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (game.tournament != null) {
                Text(
                    text = gameTournamentText(game.tournament),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = game.court.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${game.format.label} · ${playerCountText(game)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (game.forecast != null) {
                Text(
                    text = forecastText(game.forecast),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isPlaying) {
                Text(
                    text = "You're playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
