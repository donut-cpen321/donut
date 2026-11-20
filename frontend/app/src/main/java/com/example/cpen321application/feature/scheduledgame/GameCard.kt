package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.teamSize
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.PlayerSlots
import com.example.cpen321application.core.ui.components.StatusChip
import com.example.cpen321application.core.ui.components.WeatherPill
import com.example.cpen321application.core.ui.components.chipStyle
import com.example.cpen321application.core.ui.gameMetaText
import com.example.cpen321application.core.ui.gameTournamentText
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.openSpots
import com.example.cpen321application.core.ui.openSpotsText
import com.example.cpen321application.core.ui.relativeStartText
import java.time.LocalDate

/**
 * A game in the Games tab, as a sticker card: a date chip and the forecast, the court, format and
 * players, the avatars with a slot per open spot, and how many spots are left (or that you play)
 * @param game the game
 * @param isPlaying whether the signed-in user plays in it
 * @param today the phone's date, for "Today" and "Tomorrow"
 * @param onClick opens the game's screen
 */
@Composable
fun GameCard(
    game: Game,
    isPlaying: Boolean,
    onClick: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    DonutCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                text = relativeStartText(game.startsAt, today, separator = " · "),
                style = ChipStyle.DATE,
                icon = R.drawable.ic_calendar_month_filled,
            )
            Spacer(modifier = Modifier.weight(1f))
            game.forecast?.let { WeatherPill(forecast = it) }
        }
        game.tournament?.let {
            Text(
                text = gameTournamentText(it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = game.court.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = gameMetaText(game),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayerSlots(names = game.players.map { it.name }, capacity = game.format.teamSize * 2)
            GameState(game = game, isPlaying = isPlaying, modifier = Modifier.weight(1f))
            GoArrow()
        }
    }
}

// "You're playing" wins over the open spots; a full or finished game shows its status
@Composable
private fun GameState(game: Game, isPlaying: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        when {
            isPlaying && game.status in setOf(GameStatus.OPEN, GameStatus.BOOKED) -> StatusChip(
                text = "You're playing",
                style = ChipStyle.YOU,
                icon = R.drawable.ic_check,
            )
            game.status == GameStatus.OPEN -> Text(
                text = openSpotsText(openSpots(game)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            else -> StatusChip(text = game.status.label, style = game.status.chipStyle)
        }
    }
}

// The 36 dp arrow at the end of a card; the whole card is the button
@Composable
internal fun GoArrow() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
