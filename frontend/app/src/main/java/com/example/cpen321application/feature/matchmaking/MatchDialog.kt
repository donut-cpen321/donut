package com.example.cpen321application.feature.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.ui.components.CourtMap
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import kotlin.math.roundToInt

/**
 * The matched game waiting for the player's answer. It can't be closed without choosing:
 * Accept or Cancel before answering, Go to game (or Cancel) after.
 */
@Composable
fun MatchDialog(
    match: Game,
    prompt: MatchPrompt,
    isAnswering: Boolean,
    errorMessage: String?,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onGoToGame: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(matchTitle(prompt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchBody(match, prompt)
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (prompt.hasAccepted || prompt.isBooked) {
                TextButton(onClick = onGoToGame, enabled = !isAnswering) { Text("Go to game") }
            } else {
                TextButton(onClick = onAccept, enabled = !isAnswering) { Text("Accept") }
            }
        },
        dismissButton = {
            // A booked game is cancelled from its own screen in Games
            if (!prompt.isBooked) {
                TextButton(onClick = onCancel, enabled = !isAnswering) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun MatchFellThroughDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match fell through") },
        text = { Text("Tap Find a match to search again.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

private fun matchTitle(prompt: MatchPrompt): String {
    if (prompt.isBooked) {
        return "Game on"
    }
    if (prompt.hasAccepted) {
        return "You're in"
    }
    return "Match found"
}

@Composable
private fun MatchBody(match: Game, prompt: MatchPrompt) {
    if (prompt.isBooked) {
        Text("Everyone accepted. See you on court.")
        return
    }
    if (prompt.hasAccepted) {
        val names = prompt.waitingFor.joinToString(", ") { it.name }
        Text("Waiting for $names to accept.")
        return
    }
    CourtMap(
        name = match.court.name,
        latitude = match.court.latitude,
        longitude = match.court.longitude,
        googlePlaceId = null,
        onTouchedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    )
    Text(
        text = "${formatStartsAt(match.startsAt)} · ${match.format.label}",
        style = MaterialTheme.typography.titleMedium
    )
    Text(text = match.court.name, style = MaterialTheme.typography.bodyLarge)
    prompt.partners.forEach { player ->
        Text(
            text = "with ${player.name} · Elo ${player.rating.roundToInt()}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    prompt.opponents.forEach { player ->
        Text(
            text = "vs ${player.name} · Elo ${player.rating.roundToInt()}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
