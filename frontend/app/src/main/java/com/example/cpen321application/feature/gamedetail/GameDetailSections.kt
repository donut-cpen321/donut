package com.example.cpen321application.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DestructiveButton
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.LargeButtonHeight
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
            style = MaterialTheme.typography.titleLarge,
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
        StatusChip(text = gameTournamentText(tournament), style = ChipStyle.TOURNAMENT, icon = R.drawable.ic_star_filled)
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
            Button(
                onClick = onJoin,
                modifier = Modifier.height(SmallButtonHeight),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) { Text("Join team $team") }
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (actions.canReportScore) {
            Button(
                onClick = { onOpenDialog(GameDialog.SCORE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) { Text("Report final score") }
        }
        if (actions.reportScoreTooEarly) {
            Text(
                text = "You can report the final score once the game starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actions.canInvite) {
            InviteButton(enabled = !isSaving, onClick = { onOpenDialog(GameDialog.INVITE) })
        }
        if (actions.canChangeTimeOrCourt) {
            RescheduleButtons(isSaving = isSaving, onOpenDialog = onOpenDialog)
        }
        if (actions.canLeave) {
            OutlinedButton(
                onClick = { onOpenDialog(GameDialog.LEAVE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) { Text("Leave game") }
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

// Tonal: a neutral fill, so Report final score stays the one ink button
@Composable
private fun InviteButton(enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(LargeButtonHeight),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(painterResource(R.drawable.ic_person_add), contentDescription = null, modifier = Modifier.size(20.dp))
        Text("Invite friends", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RescheduleButtons(isSaving: Boolean, onOpenDialog: (GameDialog) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { onOpenDialog(GameDialog.TIME) },
            enabled = !isSaving,
            modifier = Modifier.weight(1f).height(LargeButtonHeight)
        ) { Text("Change time") }
        OutlinedButton(
            onClick = { onOpenDialog(GameDialog.COURT) },
            enabled = !isSaving,
            modifier = Modifier.weight(1f).height(LargeButtonHeight)
        ) { Text("Change court") }
    }
}
