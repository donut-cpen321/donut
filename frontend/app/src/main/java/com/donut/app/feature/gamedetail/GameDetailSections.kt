package com.donut.app.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import com.donut.app.R
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.GameTournament
import com.donut.app.core.data.model.Round
import com.donut.app.core.data.model.TEAMS
import com.donut.app.core.data.model.isClosed
import com.donut.app.core.data.model.playersOnTeam
import com.donut.app.core.data.model.teamSize
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.DestructiveButton
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.OpenSpotRow
import com.donut.app.core.ui.components.PersonRow
import com.donut.app.core.ui.components.Scoreboard
import com.donut.app.core.ui.components.ScoreboardSide
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.components.SmallButtonHeight
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.components.WeatherPill
import com.donut.app.core.ui.components.chipStyle
import com.donut.app.core.ui.formatStartsAt
import com.donut.app.core.ui.gameMetaText
import com.donut.app.core.ui.gameTournamentText
import com.donut.app.core.ui.label
import com.donut.app.core.ui.playerNames
import com.donut.app.core.ui.theme.DonutTextStyles
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
                PersonRow(name = player.name, avatarUrl = player.photoUrl) {
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
 * A score as a scoreboard, team 1 on top
 * @param rounds the game's own rounds, or a score waiting for confirmation
 * @param title e.g. "Final", followed by the format
 * @param footer e.g. who reported a score waiting for confirmation
 */
@Composable
internal fun GameScoreboard(
    game: Game,
    rounds: List<Round> = game.rounds,
    title: String = if (game.status == GameStatus.COMPLETED) "Final" else "Score",
    footer: (@Composable RowScope.() -> Unit)? = null
) {
    val team1 = game.playersOnTeam(1)
    val team2 = game.playersOnTeam(2)
    val team1Scores = rounds.map { it.team1Score }
    val team2Scores = rounds.map { it.team2Score }
    Scoreboard(
        title = "$title · ${game.format.label}",
        sides = listOf(
            ScoreboardSide(
                playerNames(team1),
                team1.firstOrNull()?.name.orEmpty(),
                team1Scores,
                team2Scores,
                team1.firstOrNull()?.photoUrl,
            ),
            ScoreboardSide(
                playerNames(team2),
                team2.firstOrNull()?.name.orEmpty(),
                team2Scores,
                team1Scores,
                team2.firstOrNull()?.photoUrl,
            )
        ),
        footer = footer
    )
}

/**
 * What a player of the game can do, most important first; Cancel game is last and in red
 */
@Composable
internal fun GamePlayerActions(
    game: Game,
    actions: GameActions,
    isSaving: Boolean,
    onOpenDialog: (GameDialog) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Shown until the game starts; once it has, the score actions take its place
        if (actions.canStartNow) {
            Button(
                onClick = { onOpenDialog(GameDialog.START) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) { Text("Start now") }
        }
        if (actions.canReportScore) {
            ScoreActions(
                game = game,
                canConfirm = actions.canConfirmScore,
                isSaving = isSaving,
                onOpenDialog = onOpenDialog
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

/**
 * Equipment section: user's equipment as checkable chips, with selection checked.
 * Hides/disables picker for closed games. Shows link to equipment screen if no equipment yet.
 * Optionally shows other players' items read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EquipmentSection(
    game: Game,
    userEquipment: List<EquipmentDto>,
    selectedEquipmentIds: Set<Int>,
    isLoadingEquipment: Boolean,
    equipmentErrorMessage: String?,
    onToggleEquipment: (Int) -> Unit,
    onOpenEquipmentScreen: () -> Unit
) {
    DonutCard {
        SectionLabel(text = "Equipment")
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoadingEquipment && userEquipment.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (userEquipment.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "No equipment yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenEquipmentScreen) {
                    Text("Go to equipment screen")
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game.isClosed) {
                    Text(
                        text = "This game is completed or cancelled. Equipment selection is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    userEquipment.forEach { item ->
                        val selected = item.id in selectedEquipmentIds
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleEquipment(item.id) },
                            enabled = !game.isClosed,
                            label = { Text(item.name) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // Other players' items (read-only)
        if (game.equipment.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Players' equipment",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            game.equipment.forEach { eq ->
                val player = game.players.firstOrNull { it.id == eq.userId }
                val playerName = player?.name ?: player?.username ?: "Player"
                Text(
                    text = "• ${eq.name} ($playerName)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (equipmentErrorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = equipmentErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// Tonal: a neutral fill, so Start now or Report final score stays the one ink button
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
