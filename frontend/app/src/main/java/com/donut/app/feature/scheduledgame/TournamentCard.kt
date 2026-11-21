package com.donut.app.feature.scheduledgame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.model.TournamentStatus
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.FillBar
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.components.WeatherPill
import com.donut.app.core.ui.formatStartTime
import com.donut.app.core.ui.label
import com.donut.app.core.ui.theme.DonutTextStyles
import com.donut.app.core.ui.tournamentPlayerCountText

// The gradient border fades from ball yellow at the top-left to the hairline colour
private const val BORDER_FADE_STOP = 0.55f

/**
 * A tournament in the Games tab: the card frame of a game with a ball-yellow edge, a Tournament
 * chip and how full the bracket is
 * @param tournament the tournament
 * @param isRegistered whether the signed-in user is registered
 * @param onClick opens the tournament's screen
 */
@Composable
fun TournamentCard(
    tournament: Tournament,
    isRegistered: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DonutCard(
        onClick = onClick,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(0f to colors.tertiary, BORDER_FADE_STOP to colors.outlineVariant)
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(text = "Tournament", style = ChipStyle.TOURNAMENT, icon = R.drawable.ic_star_filled)
                if (isRegistered) {
                    StatusChip(text = "You're in", style = ChipStyle.YOU)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            tournament.forecast?.let { WeatherPill(forecast = it) }
        }
        Text(
            text = tournament.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${formatStartTime(tournament.startsAt)} · ${tournament.court.name} · ${tournament.format.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tournament.status == TournamentStatus.REGISTRATION) {
                FillBar(
                    filled = tournament.players.size.toFloat() / tournament.capacity.coerceAtLeast(1),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                Text(text = tournamentPlayerCountText(tournament), style = DonutTextStyles.numericSmall.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                ))
            } else {
                StatusChip(text = tournament.status.label, style = ChipStyle.BOOKED)
            }
        }
    }
}
