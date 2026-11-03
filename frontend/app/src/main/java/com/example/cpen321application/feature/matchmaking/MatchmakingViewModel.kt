package com.example.cpen321application.feature.matchmaking

import androidx.lifecycle.ViewModel
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.repository.GamesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.HttpException
import java.net.HttpURLConnection

const val PERMISSION_DENIED_MESSAGE = "Location permission denied. Enter an address instead."
const val JOIN_FAILED_MESSAGE = "Could not join the queue. Check your connection and try again."
const val LEAVE_FAILED_MESSAGE = "Could not leave the queue. Check your connection and try again."
const val ACCEPT_FAILED_MESSAGE = "Could not accept the match. Check your connection and try again."
const val ACCEPT_TOO_LATE_MESSAGE = "This match starts in less than 30 minutes, so it can't be accepted anymore."
const val CANCEL_FAILED_MESSAGE = "Could not cancel the match. Check your connection and try again."
const val ACTIVE_MATCH_MESSAGE = "You already have a match coming up. Play it or cancel it first."

/**
 * Holds the location and format the user picked on the Matchmaking tab, their place in the queue,
 * and the matched game waiting for their answer.
 * The screen fetches the location (GPS or Places) and reports the result here.
 * Queue calls are suspend functions the screen launches, so the screen also runs the 10-second check.
 */
class MatchmakingViewModel(
    private val repository: MatchmakingRepository,
    private val gamesRepository: GamesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchmakingUiState())
    val uiState: StateFlow<MatchmakingUiState> = _uiState.asStateFlow()

    /**
     * Switches between current location and address, dropping the location and any
     * lookup still running for the other option
     */
    fun onOptionSelected(option: LocationOption) {
        // The queue keeps the location it was joined with
        if (_uiState.value.isSearching) {
            return
        }
        _uiState.update {
            it.copy(
                selectedOption = option,
                location = null,
                errorMessage = null,
                isLoading = false
            )
        }
    }

    /**
     * Picks singles, doubles or either. Ignored while in the queue: the player has to leave first.
     */
    fun onFormatSelected(format: MatchFormat) {
        if (_uiState.value.isSearching) {
            return
        }
        _uiState.update { it.copy(format = format) }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(isLoading = false, errorMessage = PERMISSION_DENIED_MESSAGE) }
    }

    fun onLocationLoading() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    }

    fun onLocationFound(location: MatchLocation) {
        _uiState.update { it.copy(isLoading = false, location = location, errorMessage = null) }
    }

    fun onLocationFailed(message: String) {
        _uiState.update { it.copy(isLoading = false, location = null, errorMessage = message) }
    }

    /**
     * Joins the queue with the picked format and location. Does nothing without a location, while it
     * is still loading, or while already in the queue.
     */
    suspend fun onFindMatch() {
        val state = _uiState.value
        val location = state.location
        if (!state.canFindMatch || location == null) {
            return
        }
        _uiState.update { it.copy(isUpdatingQueue = true, errorMessage = null) }

        val result = repository.joinQueue(state.format, location)
        if (result.isSuccess) {
            _uiState.update { it.copy(isUpdatingQueue = false, isSearching = true) }
            return
        }
        val message = if (result.exceptionOrNull() is ActiveMatchException) ACTIVE_MATCH_MESSAGE else JOIN_FAILED_MESSAGE
        _uiState.update { it.copy(isUpdatingQueue = false, errorMessage = message) }
    }

    /**
     * Leaves the queue. If the backend can't be reached, the player stays in it.
     */
    suspend fun onLeaveQueue() {
        val state = _uiState.value
        if (!state.isSearching || state.isUpdatingQueue) {
            return
        }
        _uiState.update { it.copy(isUpdatingQueue = true, errorMessage = null) }

        val left = repository.leaveQueue().isSuccess
        if (left) {
            _uiState.update { it.copy(isUpdatingQueue = false, isSearching = false) }
        } else {
            _uiState.update { it.copy(isUpdatingQueue = false, errorMessage = LEAVE_FAILED_MESSAGE) }
        }
    }

    /**
     * Asks the backend whether the player is still waiting. Once they are not, matchmaking found them a game,
     * which the match pop-up shows. A failed check changes nothing, the next one tries again.
     */
    suspend fun checkQueue() {
        if (!_uiState.value.isSearching) {
            return
        }
        val stillWaiting = repository.queuedFormat().getOrElse { return } != null
        // The player left the queue while this check was on its way, that is not a match
        val state = _uiState.value
        if (stillWaiting || !state.isSearching || state.isUpdatingQueue) {
            return
        }
        val match = gamesRepository.getMyGames().getOrElse { return }.pendingMatch()
        // No pending match means another player already cancelled it
        _uiState.update {
            it.copy(isSearching = false, match = match, matchFellThrough = match == null, errorMessage = null)
        }
    }

    /**
     * Picks up a search started before the app was closed
     */
    suspend fun restoreQueue() {
        val format = repository.queuedFormat().getOrNull() ?: return
        _uiState.update { it.copy(isSearching = true, format = format) }
    }

    /**
     * Shows the player's matched game that is still waiting for answers, e.g. after the app was closed
     */
    suspend fun loadMatch() {
        val match = gamesRepository.getMyGames().getOrNull()?.pendingMatch() ?: return
        _uiState.update { it.copy(match = match, errorMessage = null) }
    }

    /**
     * Accepts the match, then refreshes it to see whether everyone has
     */
    suspend fun onAccept() {
        val state = _uiState.value
        val match = state.match
        if (match == null || state.isAnswering) {
            return
        }
        _uiState.update { it.copy(isAnswering = true, errorMessage = null) }

        val error = gamesRepository.acceptMatch(match.id).exceptionOrNull()
        checkMatch()
        // An accept refused because the match was cancelled already shows "Match fell through"
        if (error != null && _uiState.value.match != null) {
            _uiState.update { it.copy(isAnswering = false, errorMessage = acceptFailedMessage(error)) }
            return
        }
        _uiState.update { it.copy(isAnswering = false) }
    }

    /**
     * Cancels the match for every player
     */
    suspend fun onCancelMatch() {
        val state = _uiState.value
        val match = state.match
        if (match == null || state.isAnswering) {
            return
        }
        _uiState.update { it.copy(isAnswering = true, errorMessage = null) }

        val cancelled = gamesRepository.cancelGame(match.id).isSuccess
        if (cancelled) {
            _uiState.update { it.copy(isAnswering = false, match = null) }
            return
        }
        checkMatch()
        // A cancel refused because the match was cancelled already shows "Match fell through"
        if (_uiState.value.match != null) {
            _uiState.update { it.copy(isAnswering = false, errorMessage = CANCEL_FAILED_MESSAGE) }
            return
        }
        _uiState.update { it.copy(isAnswering = false) }
    }

    /**
     * Refreshes the match while the pop-up is open, to see other players' answers.
     * A failed check changes nothing, the next one tries again.
     */
    suspend fun checkMatch() {
        val match = _uiState.value.match ?: return
        val game = gamesRepository.getGame(match.id).getOrElse { return }
        // The pop-up was closed while this check was on its way
        if (_uiState.value.match?.id != game.id) {
            return
        }
        if (game.status == GameStatus.CANCELLED) {
            _uiState.update { it.copy(match = null, matchFellThrough = true, errorMessage = null) }
            return
        }
        _uiState.update { it.copy(match = game) }
    }

    fun onGoToGame() {
        _uiState.update { it.copy(match = null, errorMessage = null) }
    }

    fun onMatchFellThroughSeen() {
        _uiState.update { it.copy(matchFellThrough = false) }
    }
}

private fun List<Game>.pendingMatch(): Game? =
    firstOrNull { it.source == GameSource.MATCHED && it.status == GameStatus.OPEN }

// A refused match that isn't cancelled is past the cutoff: the deadline job cancels it within a minute
private fun acceptFailedMessage(error: Throwable): String {
    if (error is HttpException && error.code() == HttpURLConnection.HTTP_CONFLICT) {
        return ACCEPT_TOO_LATE_MESSAGE
    }
    return ACCEPT_FAILED_MESSAGE
}
