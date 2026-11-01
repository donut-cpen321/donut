package com.example.cpen321application.feature.gamedetail

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.data.model.TEAMS
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.data.model.teamSize
import com.example.cpen321application.core.ui.components.CourtPickerDialog
import com.example.cpen321application.core.ui.components.DateTimePickerDialog
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import java.time.Instant
import kotlin.math.roundToInt

// Tennis matches are best of 3 or 5 sets
private const val MAX_SETS = 5

@Composable
fun GameDetailRoute(
    viewModel: GameDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    GameDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onJoin = viewModel::join,
        onLeave = viewModel::leave,
        onChangeTime = viewModel::changeTime,
        onOpenCourtPicker = viewModel::loadCourts,
        onChangeCourt = viewModel::changeCourt,
        onReportScore = viewModel::reportScore,
        onCancelGame = { viewModel.cancel(onCancelled = onBack) }
    )
}

@Composable
fun GameDetailScreen(
    uiState: GameDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onJoin: (team: Int) -> Unit,
    onLeave: () -> Unit,
    onChangeTime: (Instant) -> Unit,
    onOpenCourtPicker: () -> Unit,
    onChangeCourt: (courtId: Int) -> Unit,
    onReportScore: (rounds: List<Pair<Int, Int>>, onReported: () -> Unit) -> Unit,
    onCancelGame: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Game",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

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
                onJoin = onJoin,
                onLeave = onLeave,
                onChangeTime = onChangeTime,
                onOpenCourtPicker = onOpenCourtPicker,
                onChangeCourt = onChangeCourt,
                onReportScore = onReportScore,
                onCancelGame = onCancelGame
            )
        }
    }
}

@Composable
private fun GameDetails(
    game: Game,
    actions: GameActions,
    uiState: GameDetailUiState,
    onJoin: (team: Int) -> Unit,
    onLeave: () -> Unit,
    onChangeTime: (Instant) -> Unit,
    onOpenCourtPicker: () -> Unit,
    onChangeCourt: (courtId: Int) -> Unit,
    onReportScore: (rounds: List<Pair<Int, Int>>, onReported: () -> Unit) -> Unit,
    onCancelGame: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showCourtPicker by remember { mutableStateOf(false) }
    var showScoreForm by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            }
        }

        TEAMS.forEach { team ->
            TeamSection(
                game = game,
                team = team,
                canJoin = team in actions.joinableTeams && !uiState.isSaving,
                onJoin = { onJoin(team) }
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
            HorizontalDivider()

            if (actions.canReportScore) {
                Button(
                    onClick = { showScoreForm = true },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Report final score")
                }
            }

            if (actions.canChangeTimeOrCourt) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change time")
                    }
                    OutlinedButton(
                        onClick = {
                            onOpenCourtPicker()
                            showCourtPicker = true
                        },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change court")
                    }
                }
            }

            if (actions.canLeave) {
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    enabled = !uiState.isSaving,
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
                    onClick = { confirmCancel = true },
                    enabled = !uiState.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel game")
                }
            }
        }
    }

    if (showTimePicker) {
        DateTimePickerDialog(
            initial = game.startsAt,
            onDismiss = { showTimePicker = false },
            onConfirm = { startsAt ->
                showTimePicker = false
                onChangeTime(startsAt)
            }
        )
    }

    if (showCourtPicker) {
        CourtPickerDialog(
            courts = uiState.courts,
            selectedCourtId = game.court.id,
            isLoading = uiState.isLoadingCourts,
            errorMessage = uiState.courtsErrorMessage,
            onRetry = onOpenCourtPicker,
            onDismiss = { showCourtPicker = false },
            onSelect = { court ->
                showCourtPicker = false
                if (court.id != game.court.id) {
                    onChangeCourt(court.id)
                }
            }
        )
    }

    if (showScoreForm) {
        ReportScoreDialog(
            initialRounds = game.rounds,
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            onDismiss = { showScoreForm = false },
            onSubmit = { rounds -> onReportScore(rounds) { showScoreForm = false } }
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave this game?",
            text = "Your spot opens up for other players.",
            confirmLabel = "Leave",
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                onLeave()
            }
        )
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Cancel this game?",
            text = "The game is deleted for every player.",
            confirmLabel = "Cancel game",
            onDismiss = { confirmCancel = false },
            onConfirm = {
                confirmCancel = false
                onCancelGame()
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

/**
 * The final score form: one row per set with each team's score
 */
@Composable
private fun ReportScoreDialog(
    initialRounds: List<Round>,
    isSaving: Boolean,
    errorMessage: String?,  // the backend's answer to the last submission
    onDismiss: () -> Unit,
    onSubmit: (rounds: List<Pair<Int, Int>>) -> Unit
) {
    val rounds = remember {
        val saved = initialRounds.map { RoundInput(it.team1Score.toString(), it.team2Score.toString()) }
        mutableStateListOf(*saved.ifEmpty { listOf(RoundInput()) }.toTypedArray())
    }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final score") },
        text = {
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

                val shownError = formError ?: errorMessage
                if (shownError != null) {
                    Text(
                        text = shownError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
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

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
        }
    )
}
