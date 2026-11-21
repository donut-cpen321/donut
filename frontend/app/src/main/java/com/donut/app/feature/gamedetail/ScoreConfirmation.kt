package com.donut.app.feature.gamedetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.data.model.Round
import com.donut.app.core.data.model.ScoreProposal
import com.donut.app.core.data.model.playersOnTeam
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.ConfirmDialog
import com.donut.app.core.ui.components.DonutCard
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.PersonRow
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.playerNames
import com.donut.app.core.ui.scoresText
import com.donut.app.core.ui.theme.DonutTextStyles

/**
 * @return e.g. "Waiting for Priya Nair to confirm." or "Waiting for Alex, Priya and Sam to confirm."
 */
fun waitingForText(players: List<Player>): String {
    val names = players.map { it.name }
    val list = if (names.size <= 1) names.joinToString() else "${names.dropLast(1).joinToString()} and ${names.last()}"
    return "Waiting for $list to confirm."
}

/**
 * What the user agrees to when confirming: e.g. "Priya Nair won 6–4, 3–6, 7–5.", from the winner's side
 * @param rounds the score waiting, which always has a winner
 */
fun confirmScoreText(game: Game, rounds: List<Round>): String {
    val team1Won = rounds.count { it.team1Score > it.team2Score } * 2 > rounds.size
    val winners = game.playersOnTeam(if (team1Won) 1 else 2)
    val scores = rounds.map { if (team1Won) it.team1Score to it.team2Score else it.team2Score to it.team1Score }
    return "${playerNames(winners)} won ${scoresText(scores)}."
}

/**
 * The score waiting for confirmation while the game is played, or else the game's own score once it has one
 */
@Composable
internal fun GameScoreSection(game: Game) {
    val proposal = game.scoreProposal
    if (proposal != null && game.status == GameStatus.IN_PROGRESS) {
        ScoreConfirmationCard(game = game, proposal = proposal)
    } else if (game.rounds.isNotEmpty()) {
        GameScoreboard(game = game)
    }
}

/**
 * Closes a dialog about the score waiting once another player changes it or the game completes, so the user never
 * confirms or corrects a score they no longer see. The live update has already put the new score on screen
 */
@Composable
internal fun CloseScoreDialogWhenScoreChanges(game: Game, openDialog: GameDialog?, onClose: () -> Unit) {
    LaunchedEffect(game.scoreProposal, game.status) {
        val aboutWaitingScore = openDialog == GameDialog.CONFIRM_SCORE ||
            (openDialog == GameDialog.SCORE && game.scoreProposal != null)
        if (aboutWaitingScore) {
            onClose()
        }
    }
}

/**
 * The score a player reported while it waits for every player to confirm it: the scoreboard with who reported it,
 * then each player and whether they agree yet. Updates live as players confirm
 */
@Composable
internal fun ScoreConfirmationCard(game: Game, proposal: ScoreProposal) {
    val reporter = game.players.firstOrNull { it.id == proposal.proposedById }
    val confirmed = game.players.count { it.scoreConfirmedAt != null }

    GameScoreboard(game = game, rounds = proposal.rounds, title = "Reported") {
        Text(
            text = "Reported by ${reporter?.name ?: "a player"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = "$confirmed of ${game.players.size} confirmed", style = DonutTextStyles.numericSmall)
    }

    DonutCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        SectionLabel(text = "Confirmations", modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp))
        game.players.forEach { player ->
            PersonRow(name = player.name) {
                if (player.scoreConfirmedAt != null) {
                    StatusChip(text = "Confirmed", style = ChipStyle.OPEN, icon = R.drawable.ic_check)
                } else {
                    StatusChip(text = "Waiting", style = ChipStyle.DONE)
                }
            }
        }
    }
}

/**
 * A player's score actions once the game started: report the score, or, once one is waiting, confirm it or
 * suggest a correction, or change it while waiting for the others
 * @param canConfirm the user hasn't agreed with the score waiting yet
 */
@Composable
internal fun ScoreActions(game: Game, canConfirm: Boolean, isSaving: Boolean, onOpenDialog: (GameDialog) -> Unit) {
    val waitingFor = playersYetToConfirm(game)
    when {
        game.scoreProposal == null -> Button(
            onClick = { onOpenDialog(GameDialog.SCORE) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
        ) { Text("Report final score") }
        canConfirm -> {
            Button(
                onClick = { onOpenDialog(GameDialog.CONFIRM_SCORE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) { Text("Confirm score") }
            OutlinedButton(
                onClick = { onOpenDialog(GameDialog.SCORE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) { Text("Suggest a correction") }
        }
        else -> {
            Text(
                text = waitingForText(waitingFor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { onOpenDialog(GameDialog.SCORE) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(LargeButtonHeight)
            ) { Text("Change score") }
        }
    }
}

/**
 * Asks before agreeing with the score waiting. The score is kept from when the dialog opened, so the user confirms
 * exactly what they read; the backend refuses it if another player changed it since
 */
@Composable
internal fun ConfirmScoreDialog(
    game: Game,
    rounds: List<Round>,
    onConfirm: (rounds: List<Pair<Int, Int>>) -> Unit,
    onDismiss: () -> Unit
) {
    val isLast = playersYetToConfirm(game).size <= 1
    ConfirmDialog(
        title = "Confirm the score?",
        text = confirmScoreText(game, rounds) + if (isLast) {
            " You're the last to confirm, so the game becomes final and ratings update."
        } else {
            " The game becomes final once every player confirms."
        },
        confirmLabel = "Confirm",
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            onConfirm(rounds.map { it.team1Score to it.team2Score })
        }
    )
}
