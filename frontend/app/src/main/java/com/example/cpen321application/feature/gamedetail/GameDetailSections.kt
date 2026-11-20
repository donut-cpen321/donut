package com.example.cpen321application.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.GameTournament
import com.example.cpen321application.core.data.model.TEAMS
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.data.model.teamSize
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DestructiveButton
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.OpenSpotRow
import com.example.cpen321application.core.ui.components.PersonRow
import com.example.cpen321application.core.ui.components.Scoreboard
import com.example.cpen321application.core.ui.components.ScoreboardSide
import com.example.cpen321application.core.ui.components.SectionLabel
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import com.example.cpen321application.core.ui.components.StatusChip
import com.example.cpen321application.core.ui.components.WeatherPill
import com.example.cpen321application.core.ui.components.chipStyle
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.gameMetaText
import com.example.cpen321application.core.ui.gameTournamentText
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.playerNames
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import kotlin.math.roundToInt

/**
 * When and where: status, forecast, start, court, format and players, and the tournament it belongs to
 * @param onOpenTournament opens the bracket of the game's tournament
 */
@Composable
internal fun GameSummaryCard(game: Game, onOpenTournament: (tournamentId: String) -> Unit) {
    DonutCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = game.status.label, style = game.status.chipStyle)
            Spacer(modifier = Modifier.weight(1f))
            game.forecast?.let { WeatherPill(forecast = it) }
        }
        Text(
            text = formatStartsAt(game.startsAt),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(text = game.court.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = gameMetaText(game),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        game.tournament?.let { tournament ->
            TournamentLink(tournament = tournament, onClick = { onOpenTournament(tournament.id) })
        }
    }
}

@Composable
private fun TournamentLink(tournament: GameTournament, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Open the bracket", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(text = gameTournamentText(tournament), style = ChipStyle.TOURNAMENT, icon = R.drawable.ic_emoji_events_filled)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Both teams in one card: a header per team (with Join when the user can join it), a row per
 * player with their rating, and a dashed row per open spot
 * @param canJoin whether the user can join the given team now
 */
@Composable
internal fun TeamsCard(game: Game, canJoin: (team: Int) -> Boolean, onJoin: (team: Int) -> Unit) {
    DonutCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        TEAMS.forEachIndexed { index, team ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 6.dp))
            }
            TeamHeader(team = team, canJoin = canJoin(team), onJoin = { onJoin(team) })
            val players = game.playersOnTeam(team)
            // Not the username: until onboarding lets players pick one, it is their email
            players.forEach { player ->
                PersonRow(name = player.name) {
                    Text(text = player.rating.roundToInt().toString(), style = DonutTextStyles.numericSmall)
                }
            }
            repeat((game.format.teamSize - players.size).coerceAtLeast(0)) { OpenSpotRow() }
        }
    }
}

@Composable
private fun TeamHeader(team: Int, canJoin: Boolean, onJoin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 20.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(text = "Team $team", modifier = Modifier.weight(1f))
        if (canJoin) {
            DonutButton(text = "Join team $team", onClick = onJoin, height = SmallButtonHeight)
        }
    }
}

/**
 * The reported score as a scoreboard, team 1 on top
 */
@Composable
internal fun GameScoreboard(game: Game) {
    val team1 = game.playersOnTeam(1)
    val team2 = game.playersOnTeam(2)
    val team1Scores = game.rounds.map { it.team1Score }
    val team2Scores = game.rounds.map { it.team2Score }
    Scoreboard(
        title = "${if (game.status == GameStatus.COMPLETED) "Final" else "Score"} · ${game.format.label}",
        sides = listOf(
            ScoreboardSide(playerNames(team1), team1.firstOrNull()?.name.orEmpty(), team1Scores, team2Scores),
            ScoreboardSide(playerNames(team2), team2.firstOrNull()?.name.orEmpty(), team2Scores, team1Scores)
        )
    )
}

/**
 * What a player of the game can do, most important first; Cancel game is last and in red
 */
@Composable
internal fun GamePlayerActions(
    actions: GameActions,
    isSaving: Boolean,
    onOpenDialog: (GameDialog) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Shown until the game starts; once it has, Report final score takes its place
        if (actions.canStartNow) {
            DonutButton(
                text = "Start now",
                onClick = { onOpenDialog(GameDialog.START) },
                enabled = !isSaving,
                icon = R.drawable.ic_sports_tennis_filled,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (actions.canReportScore) {
            DonutButton(
                text = "Report final score",
                onClick = { onOpenDialog(GameDialog.SCORE) },
                enabled = !isSaving,
                icon = R.drawable.ic_emoji_events_filled,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (actions.canInvite) {
            // Ball yellow, so Start now or Report final score stays the one clay button
            DonutButton(
                text = "Invite friends",
                onClick = { onOpenDialog(GameDialog.INVITE) },
                enabled = !isSaving,
                style = ButtonStyle.TONAL,
                icon = R.drawable.ic_person_add_filled,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (actions.canChangeTimeOrCourt) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DonutButton(
                    text = "Change time",
                    onClick = { onOpenDialog(GameDialog.TIME) },
                    enabled = !isSaving,
                    style = ButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                DonutButton(
                    text = "Change court",
                    onClick = { onOpenDialog(GameDialog.COURT) },
                    enabled = !isSaving,
                    style = ButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (actions.canLeave) {
            DonutButton(
                text = "Leave game",
                onClick = { onOpenDialog(GameDialog.LEAVE) },
                enabled = !isSaving,
                style = ButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (actions.leaveTooLate) {
            Text(
                text = "You can't leave a game that starts in less than 30 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actions.canCancel) {
            DestructiveButton(text = "Cancel game", enabled = !isSaving, onClick = { onOpenDialog(GameDialog.CANCEL) })
        }
    }
}
