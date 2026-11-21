package com.donut.app.feature.tournamentdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.model.TournamentPlayer
import com.donut.app.core.data.model.TournamentStatus
import com.donut.app.core.data.model.playersOnTeam
//import com.donut.app.core.data.repository.safeLog
import com.donut.app.core.ui.FollowWhileVisible
import com.donut.app.core.ui.components.BackHeader
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.ConfirmDialog
import com.donut.app.core.ui.components.DestructiveButton
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.FillBar
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.PersonRow
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.components.WeatherPill
import com.donut.app.core.ui.formatStartsAt
import com.donut.app.core.ui.label
import com.donut.app.core.ui.teamName
import com.donut.app.core.ui.theme.DonutTextStyles
import com.donut.app.core.ui.theme.DonutTheme
import com.donut.app.core.ui.tournamentPlayerCountText
import kotlin.math.roundToInt

/**
 * What the tournament screen does when the user acts on the tournament
 * @property onJoin registers the user
 * @property onLeave unregisters the user
 * @property onCancel cancels the tournament for every player
 * @property onOpenGame opens the game of a bracket match
 */
data class TournamentDetailCallbacks(
    val onJoin: () -> Unit,
    val onLeave: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenGame: (gameId: String) -> Unit
)

// The confirmation dialogs the tournament screen opens, at most one at a time
private enum class TournamentDialog { LEAVE, CANCEL }

@Composable
fun TournamentDetailRoute(
    viewModel: TournamentDetailViewModel,
    onBack: () -> Unit,
    onOpenGame: (gameId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = remember(viewModel, onBack, onOpenGame) {
        TournamentDetailCallbacks(
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
            onCancel = { viewModel.cancel(onCancelled = onBack) },
            onOpenGame = onOpenGame
        )
    }

    // Coming back from a match's screen reconnects, so a score reported there shows here
    FollowWhileVisible(viewModel) { viewModel.followUpdates() }

    TournamentDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        callbacks = callbacks
    )
}

@Composable
fun TournamentDetailScreen(
    uiState: TournamentDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    callbacks: TournamentDetailCallbacks
) {
    val tournament = uiState.tournament
    val actions = uiState.actions

    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(title = tournament?.name ?: "Tournament", onBack = onBack)

        if (uiState.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when {
            tournament == null && uiState.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            tournament == null || actions == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.errorMessage ?: "This tournament could not be loaded.",
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            else -> TournamentDetails(
                tournament = tournament,
                actions = actions,
                uiState = uiState,
                callbacks = callbacks
            )
        }
    }
}

@Composable
private fun TournamentDetails(
    tournament: Tournament,
    actions: TournamentActions,
    uiState: TournamentDetailUiState,
    callbacks: TournamentDetailCallbacks
) {
    var openDialog by remember { mutableStateOf<TournamentDialog?>(null) }
    val bracket = remember(tournament) { bracketColumns(tournament) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TournamentSummaryCard(tournament = tournament)

        val winningTeam = tournament.winningTeam
        if (tournament.status == TournamentStatus.COMPLETED && winningTeam != null) {
            ChampionBanner(teamName = teamName(tournament.playersOnTeam(winningTeam)))
        }

        when (tournament.status) {
            TournamentStatus.REGISTRATION -> RegistrationSection(
                tournament = tournament,
                actions = actions,
                isSaving = uiState.isSaving,
                onJoin = callbacks.onJoin,
                onLeave = { openDialog = TournamentDialog.LEAVE },
                onCancel = { openDialog = TournamentDialog.CANCEL }
            )
            TournamentStatus.IN_PROGRESS, TournamentStatus.COMPLETED ->
                TournamentBracket(columns = bracket, onMatchClick = callbacks.onOpenGame)
            TournamentStatus.CANCELLED -> Text(
                text = "This tournament was cancelled.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    openDialog?.let { dialog ->
        TournamentDialogContent(dialog = dialog, callbacks = callbacks, onDismiss = { openDialog = null })
    }
}

// The frame of a tournament card in the Games tab: a ball-yellow edge fading into the hairline
private const val BORDER_FADE_STOP = 0.55f

@Composable
private fun TournamentSummaryCard(tournament: Tournament) {
    val colors = MaterialTheme.colorScheme
    DonutCard(
        border = BorderStroke(1.dp, Brush.linearGradient(0f to colors.tertiary, BORDER_FADE_STOP to colors.outlineVariant))
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = tournament.status.label, style = tournament.status.chipStyle)
            Spacer(modifier = Modifier.weight(1f))
            tournament.forecast?.let { WeatherPill(forecast = it) }
        }
        Text(
            text = formatStartsAt(tournament.startsAt),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(text = tournament.court.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${tournament.format.label} · Created by ${tournament.creator.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FillBar(
                filled = tournament.players.size.toFloat() / tournament.capacity.coerceAtLeast(1),
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text(text = tournamentPlayerCountText(tournament), style = DonutTextStyles.numericSmall)
        }
    }
}

private val TournamentStatus.chipStyle: ChipStyle
    get() = when (this) {
        TournamentStatus.REGISTRATION -> ChipStyle.OPEN
        TournamentStatus.IN_PROGRESS -> ChipStyle.BOOKED
        TournamentStatus.COMPLETED -> ChipStyle.DONE
        TournamentStatus.CANCELLED -> ChipStyle.CANCELLED
    }

@Composable
private fun RegistrationSection(
    tournament: Tournament,
    actions: TournamentActions,
    isSaving: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "Registered players", style = MaterialTheme.typography.titleMedium)
        RegisteredPlayers(players = tournament.players)

        if (actions.registrationClosed) {
            Text(
                text = "Registration closed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = registrationNote(tournament, actions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (actions.canJoin) {
            Button(onClick = onJoin, enabled = !isSaving, modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)) {
                Text("Join tournament")
            }
        }
        if (actions.canLeave) {
            OutlinedButton(
                onClick = onLeave,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) {
                Text("Leave tournament")
            }
        }
        if (actions.canCancel) {
            DestructiveButton(text = "Cancel tournament", enabled = !isSaving, onClick = onCancel)
        }
    }
}

// One card, a row per player in the order they joined, with their rating
@Composable
private fun RegisteredPlayers(players: List<TournamentPlayer>) {
    DonutCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        if (players.isEmpty()) {
            Text(
                text = "No one has registered yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        players.forEachIndexed { index, player ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 68.dp, end = 20.dp)
                )
            }
            // safeLog("TournamentDetail", "RegisteredPlayers rendering ${player.name} (${player.id}): avatarUrl = ${player.photoUrl}")
            PersonRow(name = player.name, avatarUrl = player.photoUrl) {
                Text(text = player.rating.roundToInt().toString(), style = DonutTextStyles.numericSmall)
            }
        }
    }
}

private fun registrationNote(tournament: Tournament, actions: TournamentActions): String = when {
    actions.registrationClosed -> "The tournament started before every spot was filled, so it can't be played."
    tournament.format == Format.DOUBLES ->
        "Once all ${tournament.capacity} spots are filled, players are paired into teams by rating " +
            "and the bracket is drawn."
    else -> "Once all ${tournament.capacity} spots are filled, the bracket is drawn by rating."
}

// The winner on the inverse surface, next to a ball-yellow trophy tile
@Composable
private fun ChampionBanner(teamName: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(DonutTheme.colors.highlight, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_emoji_events_filled),
                    contentDescription = null,
                    tint = DonutTheme.colors.onHighlight
                )
            }
            Column {
                Text(
                    text = "Champion",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inverseOnSurface.copy(alpha = MUTED_ON_INVERSE)
                )
                Text(text = teamName, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private const val MUTED_ON_INVERSE = 0.7f

@Composable
private fun TournamentDialogContent(
    dialog: TournamentDialog,
    callbacks: TournamentDetailCallbacks,
    onDismiss: () -> Unit
) {
    when (dialog) {
        TournamentDialog.LEAVE -> ConfirmDialog(
            title = "Leave this tournament?",
            text = "Your spot opens up for other players.",
            confirmLabel = "Leave",
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                callbacks.onLeave()
            }
        )
        TournamentDialog.CANCEL -> ConfirmDialog(
            title = "Cancel this tournament?",
            text = "The tournament is cancelled for every registered player.",
            confirmLabel = "Cancel tournament",
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                callbacks.onCancel()
            }
        )
    }
}
