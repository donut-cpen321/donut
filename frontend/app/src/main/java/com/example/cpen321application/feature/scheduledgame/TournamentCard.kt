package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentStatus
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.FillBar
import com.example.cpen321application.core.ui.components.StatusChip
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.components.WeatherPill
import com.example.cpen321application.core.ui.formatStartTime
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.relativeStartText
import com.example.cpen321application.core.ui.theme.StickerBorderWidth
import com.example.cpen321application.core.ui.tournamentPlayerCountText
import java.time.LocalDate

// The trophy badge leans the other way from the W / L badges and breaks out of the card's top edge
private const val TROPHY_TILT = 10f
private val TROPHY_SIZE = 50.dp
// Brackets up to this size show a dot per spot; bigger ones show a bar
private const val MAX_DOTS = 16

/**
 * A tournament in the Games tab: a ball-yellow sticker card with a white trophy badge breaking out
 * of its top edge, the name, where and when, and a dot per spot in the bracket
 * @param tournament the tournament
 * @param isRegistered whether the signed-in user is registered
 * @param today the phone's date, for "Today" and "Tomorrow"
 * @param onClick opens the tournament's screen
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentCard(
    tournament: Tournament,
    isRegistered: Boolean,
    onClick: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val colors = MaterialTheme.colorScheme
    Box {
        DonutCard(onClick = onClick, color = colors.tertiary) {
            val day = relativeStartText(tournament.startsAt, today, separator = " · ").substringBefore(" · ")
            // The trophy covers the top-right corner, so the first line stops short of it
            Text(
                text = "Tournament · $day".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = TROPHY_SIZE + 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tournament.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${tournament.court.name} · ${tournament.format.label} · ${formatStartTime(tournament.startsAt)}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Wraps onto a second line when the bracket, You're in and the forecast don't fit on one
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RegistrationProgress(tournament = tournament, modifier = Modifier.align(Alignment.CenterVertically))
                if (isRegistered) {
                    StatusChip(text = "You're in", style = ChipStyle.YOU)
                }
                tournament.forecast?.let { WeatherPill(forecast = it) }
            }
        }
        TrophyBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-14).dp, y = (-10).dp),
        )
    }
}

// While registration is open: a dot per spot (filled once taken) and "3/8 players"; after that, the status
@Composable
private fun RegistrationProgress(tournament: Tournament, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onTertiary
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            tournament.status != TournamentStatus.REGISTRATION ->
                StatusChip(text = tournament.status.label, style = ChipStyle.OUTLINE)
            tournament.capacity <= MAX_DOTS -> {
                Row(modifier = Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(tournament.capacity) { index ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (index < tournament.players.size) ink else Color.Transparent, CircleShape)
                                .border(StickerBorderWidth, ink, CircleShape),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(3.dp))
                Text(text = tournamentPlayerCountText(tournament), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            else -> {
                FillBar(
                    filled = tournament.players.size.toFloat() / tournament.capacity,
                    modifier = Modifier.width(120.dp),
                )
                Text(text = tournamentPlayerCountText(tournament), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

// A white sticker with a clay trophy; decorative
@Composable
private fun TrophyBadge(modifier: Modifier = Modifier) {
    StickerSurface(
        modifier = modifier
            .size(TROPHY_SIZE)
            .rotate(TROPHY_TILT),
        shape = CircleShape,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_emoji_events_filled),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp),
        )
    }
}
