package com.donut.app.feature.scheduledgame

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.teamSize
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.PlayerSlots
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.components.WeatherPill
import com.donut.app.core.ui.components.chipStyle
import com.donut.app.core.ui.formatStartTime
import com.donut.app.core.ui.gameMetaText
import com.donut.app.core.ui.gameTournamentText
import com.donut.app.core.ui.label
import com.donut.app.core.ui.openSpots
import com.donut.app.core.ui.openSpotsText
import com.donut.app.core.ui.theme.DonutTextStyles

/**
 * A game in the Games tab: start time (the day is in the header above), forecast, court, format,
 * players with a slot per open spot, and its state
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
    DonutCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = formatStartTime(game.startsAt), style = DonutTextStyles.numericTitle)
            Spacer(modifier = Modifier.weight(1f))
            game.forecast?.let { WeatherPill(forecast = it) }
        }
        game.tournament?.let {
            Text(
                text = gameTournamentText(it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            text = game.court.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = gameMetaText(game),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerSlots(
                names = game.players.map { it.name },
                capacity = game.format.teamSize * 2,
                avatarUrls = game.players.map { it.photoUrl },
            )
            Spacer(modifier = Modifier.weight(1f))
            GameStateChip(game = game, isPlaying = isPlaying)
        }
    }
}

// "You're playing" wins over the open spots; a full or finished game shows its status
@Composable
private fun GameStateChip(game: Game, isPlaying: Boolean) {
    when {
        isPlaying && game.status == GameStatus.OPEN -> StatusChip(text = "You're playing", style = ChipStyle.YOU)
        game.status == GameStatus.OPEN -> StatusChip(text = openSpotsText(openSpots(game)), style = ChipStyle.OPEN)
        isPlaying && game.status == GameStatus.BOOKED -> StatusChip(
            text = "You're playing",
            style = ChipStyle.YOU,
            icon = R.drawable.ic_check
        )
        else -> StatusChip(text = game.status.label, style = game.status.chipStyle)
    }
}
