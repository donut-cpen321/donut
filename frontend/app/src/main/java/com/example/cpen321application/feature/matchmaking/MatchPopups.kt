package com.example.cpen321application.feature.matchmaking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHECK_INTERVAL_MS = 10_000L

/**
 * Watches the queue and shows the match pop-up over whichever screen is open, so a match found
 * while the player is on another tab still pops up.
 * @param username the signed-in user, to tell which side of the match they are on
 * @param onGoToGame opens the matched game's screen
 */
@Composable
fun MatchPopups(
    viewModel: MatchmakingViewModel,
    username: String,
    onGoToGame: (gameId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // The player may have joined the queue, or been matched, before the app was closed
    LaunchedEffect(Unit) {
        viewModel.restoreQueue()
        viewModel.loadMatch()
    }

    // While waiting, ask the backend every 10 seconds whether matchmaking found a game.
    // Restarts when isSearching changes and stops on sign-out.
    LaunchedEffect(uiState.isSearching) {
        while (uiState.isSearching) {
            delay(CHECK_INTERVAL_MS)
            viewModel.checkQueue()
        }
    }

    // While the match pop-up is open, ask the backend every 10 seconds whether the other players answered.
    // Restarts when the match changes and stops when the pop-up closes.
    val match = uiState.match
    LaunchedEffect(match?.id) {
        while (viewModel.uiState.value.match != null) {
            delay(CHECK_INTERVAL_MS)
            viewModel.checkMatch()
        }
    }

    if (match != null) {
        MatchDialog(
            match = match,
            prompt = matchPrompt(match, username),
            isAnswering = uiState.isAnswering,
            errorMessage = uiState.errorMessage,
            onAccept = { scope.launch { viewModel.onAccept() } },
            onCancel = { scope.launch { viewModel.onCancelMatch() } },
            onGoToGame = {
                viewModel.onGoToGame()
                onGoToGame(match.id)
            }
        )
    }

    if (uiState.matchFellThrough) {
        MatchFellThroughDialog(onDismiss = viewModel::onMatchFellThroughSeen)
    }
}
