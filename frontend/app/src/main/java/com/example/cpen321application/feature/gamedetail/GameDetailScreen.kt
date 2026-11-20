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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.ui.FollowWhileVisible
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.ConfirmDialog
import com.example.cpen321application.core.ui.components.CourtPickerDialog
import com.example.cpen321application.core.ui.components.DateTimePickerDialog
import com.example.cpen321application.core.ui.components.DonutTextField
import com.example.cpen321application.core.ui.components.SectionLabel
import com.example.cpen321application.core.ui.components.UnderlinedTextButton
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import java.time.Instant

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
 * @property onStartNow starts the booked game now, before its start time
 * @property onCancelGame cancels the game for every player
 * @property onOpenTournament opens the bracket of the tournament with this id, for a tournament game
 * @property onOpenInvite loads the friends who can be invited
 * @property onInvite invites the friend with this user id
 */
data class GameDetailCallbacks(
    val onJoin: (team: Int) -> Unit,
    val onLeave: () -> Unit,
    val onChangeTime: (Instant) -> Unit,
    val onOpenCourtPicker: () -> Unit,
    val onChangeCourt: (courtId: Int) -> Unit,
    val onReportScore: (rounds: List<Pair<Int, Int>>, onReported: () -> Unit) -> Unit,
    val onStartNow: () -> Unit,
    val onCancelGame: () -> Unit,
    val onOpenTournament: (tournamentId: String) -> Unit,
    val onOpenInvite: () -> Unit,
    val onInvite: (userId: String) -> Unit
)

// What a confirm dialog asks before the game is started early, left or cancelled
private data class GameConfirmation(val title: String, val text: String, val confirmLabel: String)

private val START_CONFIRMATION = GameConfirmation(
    title = "Start this game now?",
    text = "The start time moves to now for every player, so you can report the score once you've played.",
    confirmLabel = "Start now"
)
private val LEAVE_CONFIRMATION = GameConfirmation(
    title = "Leave this game?",
    text = "Your spot opens up for other players.",
    confirmLabel = "Leave"
)
private val CANCEL_CONFIRMATION = GameConfirmation(
    title = "Cancel this game?",
    text = "The game is cancelled for every player.",
    confirmLabel = "Cancel game"
)

// The dialogs the game screen opens, at most one at a time
internal enum class GameDialog { TIME, COURT, SCORE, START, LEAVE, CANCEL, INVITE }

@Composable
fun GameDetailRoute(
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    onOpenTournament: (tournamentId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    FollowWhileVisible(viewModel) { viewModel.followUpdates() }

    val callbacks = remember(viewModel, onBack, onOpenTournament) {
        GameDetailCallbacks(
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
            onChangeTime = viewModel::changeTime,
            onOpenCourtPicker = viewModel::loadCourts,
            onChangeCourt = viewModel::changeCourt,
            onReportScore = viewModel::reportScore,
            onStartNow = viewModel::startNow,
            onCancelGame = { viewModel.cancel(onCancelled = onBack) },
            onOpenTournament = onOpenTournament,
            onOpenInvite = viewModel::loadFriends,
            onInvite = viewModel::invite
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
                UnderlinedTextButton(text = "Retry", onClick = onRetry)
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
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GameSummaryCard(game = game, onOpenTournament = callbacks.onOpenTournament)

        TeamsCard(
            game = game,
            canJoin = { team -> team in actions.joinableTeams && !uiState.isSaving },
            onJoin = callbacks.onJoin
        )

        if (game.rounds.isNotEmpty()) {
            GameScoreboard(game = game)
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (actions.isPlayer) {
            GamePlayerActions(
                actions = actions,
                isSaving = uiState.isSaving,
                onOpenDialog = { dialog ->
                    when (dialog) {
                        GameDialog.COURT -> callbacks.onOpenCourtPicker()
                        GameDialog.INVITE -> callbacks.onOpenInvite()
                        else -> Unit
                    }
                    openDialog = dialog
                }
            )
        }
    }

    openDialog?.let { dialog ->
        OpenGameDialog(
            dialog = dialog,
            game = game,
            uiState = uiState,
            callbacks = callbacks,
            onDismiss = { openDialog = null }
        )
    }
}

@Composable
private fun OpenGameDialog(
    dialog: GameDialog,
    game: Game,
    uiState: GameDetailUiState,
    callbacks: GameDetailCallbacks,
    onDismiss: () -> Unit
) {
    if (dialog == GameDialog.INVITE) {
        InviteFriendsDialog(
            friends = uiState.invite.friends?.let { invitableFriends(it, game) },
            invite = uiState.invite,
            onRetry = callbacks.onOpenInvite,
            onInvite = callbacks.onInvite,
            onDismiss = onDismiss
        )
    } else {
        GameDialogContent(
            dialog = dialog,
            game = game,
            uiState = uiState,
            callbacks = callbacks,
            onDismiss = onDismiss
        )
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
        GameDialog.START -> GameConfirmDialog(START_CONFIRMATION, callbacks.onStartNow, onDismiss)
        GameDialog.LEAVE -> GameConfirmDialog(LEAVE_CONFIRMATION, callbacks.onLeave, onDismiss)
        GameDialog.CANCEL -> GameConfirmDialog(CANCEL_CONFIRMATION, callbacks.onCancelGame, onDismiss)
        // Shown by GameDetails, since it needs the friends list
        GameDialog.INVITE -> Unit
    }
}

// Asks before starting, leaving or cancelling the game, then closes and sends the action
@Composable
private fun GameConfirmDialog(confirmation: GameConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = confirmation.title,
        text = confirmation.text,
        confirmLabel = confirmation.confirmLabel,
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            onConfirm()
        }
    )
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
            SectionLabel(text = "Team 1", modifier = Modifier.width(80.dp))
            Spacer(modifier = Modifier.width(8.dp))
            SectionLabel(text = "Team 2", modifier = Modifier.width(80.dp))
        }

        rounds.forEachIndexed { index, round ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set ${index + 1}", modifier = Modifier.width(48.dp), style = MaterialTheme.typography.bodyMedium)
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
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove set ${index + 1}")
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
    DonutTextField(
        value = value,
        // Digits only, and at most two of them
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(2)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = DonutTextStyles.numericSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
        modifier = Modifier.width(80.dp)
    )
}
