package com.example.cpen321application.feature.tournamentdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentStatus
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.ConfirmDialog
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.teamName
import com.example.cpen321application.core.ui.tournamentPlayerCountText
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

    // Runs each time the screen is shown, so coming back from a match's screen shows its new score
    LaunchedEffect(Unit) {
        viewModel.load()
    }

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
            .padding(16.dp),
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

@Composable
private fun TournamentSummaryCard(tournament: Tournament) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = formatStartsAt(tournament.startsAt), style = MaterialTheme.typography.titleLarge)
            Text(text = tournament.court.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${tournament.format.label} · ${tournament.status.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${tournamentPlayerCountText(tournament)} · Created by ${tournament.creator.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Registered players", style = MaterialTheme.typography.titleMedium)
        // Not the username: until onboarding lets players pick one, it is their email
        tournament.players.forEach { player ->
            Text(text = "${player.name} · ${player.rating.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        }

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
            Button(onClick = onJoin, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                Text("Join tournament")
            }
        }
        if (actions.canLeave) {
            OutlinedButton(onClick = onLeave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                Text("Leave tournament")
            }
        }
        if (actions.canCancel) {
            TextButton(
                onClick = onCancel,
                enabled = !isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel tournament")
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

@Composable
private fun ChampionBanner(teamName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Champion", style = MaterialTheme.typography.labelLarge)
                Text(text = teamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
