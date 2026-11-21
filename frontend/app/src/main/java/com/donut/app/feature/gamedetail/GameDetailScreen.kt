package com.donut.app.feature.gamedetail

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.Round
import com.donut.app.core.ui.FollowWhileVisible
import com.donut.app.core.ui.components.BackHeader
import com.donut.app.core.ui.components.ConfirmDialog
import com.donut.app.core.ui.components.CourtPickerDialog
import com.donut.app.core.ui.components.DateTimePickerDialog
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.theme.DonutTextStyles
import com.donut.app.core.ui.theme.TextFieldShape
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
 * @property onConfirmScore agrees with the score waiting, as the user saw it
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
    val onConfirmScore: (rounds: List<Pair<Int, Int>>) -> Unit,
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
internal enum class GameDialog { TIME, COURT, SCORE, CONFIRM_SCORE, START, LEAVE, CANCEL, INVITE }

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
            onConfirmScore = viewModel::confirmScore,
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

    CloseScoreDialogWhenScoreChanges(game = game, openDialog = openDialog, onClose = { openDialog = null })

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

        GameScoreSection(game = game)

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (actions.isPlayer) {
            GamePlayerActions(
                game = game,
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
            form = scoreFormText(game, uiState.actions?.canConfirmScore == true),
            initialRounds = game.scoreProposal?.rounds ?: game.rounds,
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            onDismiss = onDismiss,
            onSubmit = { rounds -> callbacks.onReportScore(rounds, onDismiss) }
        )
        GameDialog.CONFIRM_SCORE -> {
            // Kept from when the dialog opened: a changed score closes it instead (see GameDetails)
            val rounds = remember { game.scoreProposal?.rounds.orEmpty() }
            ConfirmScoreDialog(
                game = game,
                rounds = rounds,
                onConfirm = callbacks.onConfirmScore,
                onDismiss = onDismiss
            )
        }
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

// The score form's title and the note under it: reporting a first score, correcting someone else's,
// or changing one's own
internal data class ScoreFormText(val title: String, val note: String)

internal fun scoreFormText(game: Game, canConfirm: Boolean): ScoreFormText = when {
    game.scoreProposal == null -> ScoreFormText(
        title = "Final score",
        note = "Every player confirms the score before the game is final."
    )
    canConfirm -> ScoreFormText(
        title = "Suggest a correction",
        note = "Your score replaces the one reported, and every player confirms again."
    )
    else -> ScoreFormText(
        title = "Change score",
        note = "Your new score replaces this one, and every player confirms again."
    )
}

@Composable
private fun ReportScoreDialog(
    form: ScoreFormText,
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
        title = { Text(form.title) },
        text = { ScoreForm(rounds = rounds, note = form.note, error = formError ?: errorMessage) },
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
private fun ScoreForm(rounds: SnapshotStateList<RoundInput>, note: String, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
    OutlinedTextField(
        value = value,
        // Digits only, and at most two of them
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(2)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = TextFieldShape,
        textStyle = DonutTextStyles.numericSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
        modifier = Modifier.width(80.dp)
    )
}
