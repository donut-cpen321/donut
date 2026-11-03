package com.example.cpen321application.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.GameTournament
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.data.model.TEAMS
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.data.model.teamSize
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.ConfirmDialog
import com.example.cpen321application.core.ui.components.CourtPickerDialog
import com.example.cpen321application.core.ui.components.DateTimePickerDialog
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.gameTournamentText
import com.example.cpen321application.core.ui.label
import java.time.Instant
import kotlin.math.roundToInt

// Tennis matches are best of 3 or 5 sets
private const val MAX_SETS = 5

/**
 * What the game screen does when the user acts on the game
 * @property onJoin joins the given team
 * @property onLeave leaves the game
 * @property onChangeTime moves the game to a new start time
 * @property onOpenCourtPicker loads the courts the game can move to
 * @property onChangeCourt moves the game to the court with this id
 * @property onReportScore reports the final score, then calls onReported if the backend accepted it
 * @property onCancelGame cancels the game for every player
 * @property onOpenTournament opens the bracket of the tournament with this id, for a tournament game
 */
data class GameDetailCallbacks(
    val onJoin: (team: Int) -> Unit,
    val onLeave: () -> Unit,
    val onChangeTime: (Instant) -> Unit,
    val onOpenCourtPicker: () -> Unit,
    val onChangeCourt: (courtId: Int) -> Unit,
    val onReportScore: (rounds: List<Pair<Int, Int>>, onReported: () -> Unit) -> Unit,
    val onCancelGame: () -> Unit,
    val onOpenTournament: (tournamentId: String) -> Unit
)

// The dialogs the game screen opens, at most one at a time
private enum class GameDialog { TIME, COURT, SCORE, LEAVE, CANCEL }

@Composable
fun GameDetailRoute(
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    onOpenTournament: (tournamentId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val callbacks = remember(viewModel, onBack, onOpenTournament) {
        GameDetailCallbacks(
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
            onChangeTime = viewModel::changeTime,
            onOpenCourtPicker = viewModel::loadCourts,
            onChangeCourt = viewModel::changeCourt,
            onReportScore = viewModel::reportScore,
            onCancelGame = { viewModel.cancel(onCancelled = onBack) },
            onOpenTournament = onOpenTournament
        )
    }

    GameDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        callbacks = callbacks
    )
}

@Composable
fun GameDetailScreen(
    uiState: GameDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    callbacks: GameDetailCallbacks
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(title = "Game", onBack = onBack)

        if (uiState.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val game = uiState.game
        val actions = uiState.actions
        when {
            game == null && uiState.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            game == null || actions == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.errorMessage ?: "This game could not be loaded.",
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            else -> GameDetails(
                game = game,
                actions = actions,
                uiState = uiState,
                callbacks = callbacks
            )
        }
    }
}

@Composable
private fun GameDetails(
    game: Game,
    actions: GameActions,
    uiState: GameDetailUiState,
    callbacks: GameDetailCallbacks
) {
    var openDialog by remember { mutableStateOf<GameDialog?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GameSummaryCard(game = game, onOpenTournament = callbacks.onOpenTournament)

        TEAMS.forEach { team ->
            TeamSection(
                game = game,
                team = team,
                canJoin = team in actions.joinableTeams && !uiState.isSaving,
                onJoin = { callbacks.onJoin(team) }
            )
        }

        if (game.rounds.isNotEmpty()) {
            ScoreSection(rounds = game.rounds, isFinal = game.status == GameStatus.COMPLETED)
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (actions.isPlayer) {
            PlayerActions(
                actions = actions,
                isSaving = uiState.isSaving,
                onOpenDialog = { dialog ->
                    if (dialog == GameDialog.COURT) {
                        callbacks.onOpenCourtPicker()
                    }
                    openDialog = dialog
                }
            )
        }
    }

    openDialog?.let { dialog ->
        GameDialogContent(
            dialog = dialog,
            game = game,
            uiState = uiState,
            callbacks = callbacks,
            onDismiss = { openDialog = null }
        )
    }
}

@Composable
private fun GameSummaryCard(game: Game, onOpenTournament: (tournamentId: String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = formatStartsAt(game.startsAt), style = MaterialTheme.typography.titleLarge)
            Text(text = game.court.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${game.format.label} · ${game.status.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (game.tournament != null) {
                TournamentLink(tournament = game.tournament, onClick = { onOpenTournament(game.tournament.id) })
            }
        }
    }
}

@Composable
private fun TournamentLink(tournament: GameTournament, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open the bracket", onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = gameTournamentText(tournament),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PlayerActions(
    actions: GameActions,
    isSaving: Boolean,
    onOpenDialog: (GameDialog) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HorizontalDivider()

        if (actions.canReportScore) {
            Button(
                onClick = { onOpenDialog(GameDialog.SCORE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report final score")
            }
        }

        if (actions.canChangeTimeOrCourt) {
            RescheduleButtons(isSaving = isSaving, onOpenDialog = onOpenDialog)
        }

        if (actions.canLeave) {
            OutlinedButton(
                onClick = { onOpenDialog(GameDialog.LEAVE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Leave game")
            }
        }
        if (actions.leaveTooLate) {
            Text(
                text = "You can't leave a game that starts in less than 30 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (actions.canCancel) {
            TextButton(
                onClick = { onOpenDialog(GameDialog.CANCEL) },
                enabled = !isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel game")
            }
        }
    }
}

@Composable
private fun RescheduleButtons(isSaving: Boolean, onOpenDialog: (GameDialog) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onOpenDialog(GameDialog.TIME) },
            enabled = !isSaving,
            modifier = Modifier.weight(1f)
        ) {
            Text("Change time")
        }
        OutlinedButton(
            onClick = { onOpenDialog(GameDialog.COURT) },
            enabled = !isSaving,
            modifier = Modifier.weight(1f)
        ) {
            Text("Change court")
        }
    }
}

@Composable
private fun GameDialogContent(
    dialog: GameDialog,
    game: Game,
    uiState: GameDetailUiState,
    callbacks: GameDetailCallbacks,
    onDismiss: () -> Unit
) {
    when (dialog) {
        GameDialog.TIME -> DateTimePickerDialog(
            initial = game.startsAt,
            onDismiss = onDismiss,
            onConfirm = { startsAt ->
                onDismiss()
                callbacks.onChangeTime(startsAt)
            }
        )
        GameDialog.COURT -> CourtPickerDialog(
            courts = uiState.courts,
            selectedCourtId = game.court.id,
            isLoading = uiState.isLoadingCourts,
            errorMessage = uiState.courtsErrorMessage,
            onRetry = callbacks.onOpenCourtPicker,
            onDismiss = onDismiss,
            onSelect = { court ->
                onDismiss()
                if (court.id != game.court.id) {
                    callbacks.onChangeCourt(court.id)
                }
            }
        )
        GameDialog.SCORE -> ReportScoreDialog(
            initialRounds = game.rounds,
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            onDismiss = onDismiss,
            onSubmit = { rounds -> callbacks.onReportScore(rounds, onDismiss) }
        )
        GameDialog.LEAVE -> ConfirmDialog(
            title = "Leave this game?",
            text = "Your spot opens up for other players.",
            confirmLabel = "Leave",
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                callbacks.onLeave()
            }
        )
        GameDialog.CANCEL -> ConfirmDialog(
            title = "Cancel this game?",
            text = "The game is cancelled for every player.",
            confirmLabel = "Cancel game",
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                callbacks.onCancelGame()
            }
        )
    }
}

@Composable
private fun TeamSection(
    game: Game,
    team: Int,
    canJoin: Boolean,
    onJoin: () -> Unit
) {
    val players = game.playersOnTeam(team)
    val openSpots = (game.format.teamSize - players.size).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Team $team",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (canJoin) {
                Button(onClick = onJoin) { Text("Join team $team") }
            }
        }

        // Not the username: until onboarding lets players pick one, it is their email
        players.forEach { player ->
            Text(
                text = "${player.name} · ${player.rating.roundToInt()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        repeat(openSpots) {
            Text(
                text = "Open spot",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreSection(rounds: List<Round>, isFinal: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (isFinal) "Final score" else "Score",
            style = MaterialTheme.typography.titleMedium
        )
        rounds.forEach { round ->
            Text(
                text = "Set ${round.number}: ${round.team1Score} – ${round.team2Score}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReportScoreDialog(
    initialRounds: List<Round>,
    isSaving: Boolean,
    errorMessage: String?,  // the backend's answer to the last submission
    onDismiss: () -> Unit,
    onSubmit: (rounds: List<Pair<Int, Int>>) -> Unit
) {
    val rounds = remember {
        initialRounds
            .map { RoundInput(it.team1Score.toString(), it.team2Score.toString()) }
            .ifEmpty { listOf(RoundInput()) }
            .toMutableStateList()
    }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final score") },
        text = { ScoreForm(rounds = rounds, error = formError ?: errorMessage) },
        confirmButton = {
            TextButton(
                onClick = {
                    formError = scoreError(rounds)
                    if (formError == null) {
                        onSubmit(toScores(rounds))
                    }
                },
                enabled = !isSaving
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScoreForm(rounds: SnapshotStateList<RoundInput>, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(48.dp))
            Text("Team 1", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Team 2", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge)
        }

        rounds.forEachIndexed { index, round ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set ${index + 1}", modifier = Modifier.width(48.dp))
                ScoreField(
                    value = round.team1,
                    onValueChange = { rounds[index] = round.copy(team1 = it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                ScoreField(
                    value = round.team2,
                    onValueChange = { rounds[index] = round.copy(team2 = it) }
                )
                if (rounds.size > 1) {
                    IconButton(onClick = { rounds.removeAt(index) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove set ${index + 1}")
                    }
                }
            }
        }

        if (rounds.size < MAX_SETS) {
            TextButton(onClick = { rounds.add(RoundInput()) }) { Text("Add set") }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ScoreField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        // Digits only, and at most two of them
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(2)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(80.dp)
    )
}
