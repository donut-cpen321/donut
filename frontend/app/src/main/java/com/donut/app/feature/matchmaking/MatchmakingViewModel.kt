package com.donut.app.feature.matchmaking

import androidx.lifecycle.ViewModel
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.repository.GameListUpdate
import com.donut.app.core.data.repository.GameListUpdates
import com.donut.app.core.data.repository.GamesRepository
import com.donut.app.core.data.repository.reconnecting
import com.donut.app.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
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
const val ALREADY_ACCEPTED_ERROR = "Match already accepted"

/**
 * Holds the location and format the user picked on the Matchmaking tab, their place in the queue,
 * and the matched game waiting for their answer.
 * The screen fetches the location (GPS or Places) and reports the result here.
 * Queue calls are suspend functions the screen launches. MatchPopups calls refresh() when the user's games change
 * live (followMatches), on match pushes, and when the app comes back, so a match shows even if its push is lost.
 */
class MatchmakingViewModel(
    private val repository: MatchmakingRepository,
    private val gamesRepository: GamesRepository,
    private val gameListUpdates: GameListUpdates
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchmakingUiState())
    val uiState: StateFlow<MatchmakingUiState> = _uiState.asStateFlow()

    // The match the player left with "Go to game", so refreshes don't reopen its pop-up while others answer
    private var leftMatchId: String? = null

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
            // nearby = null: the dots of an earlier search are not around this one. A match shown while the join
            // was on its way (its push or the live games beat the answer) means the search is already over
            _uiState.update { it.copy(isUpdatingQueue = false, isSearching = it.match == null, nearby = null) }
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
     * Brings the queue and the match pop-up up to date with the backend: after a match push, or when the app
     * comes back. A failed check changes nothing, the next push or return to the app tries again.
     */
    suspend fun refresh() {
        val state = _uiState.value
        if (state.match != null) {
            checkMatch()
            return
        }
        if (state.isSearching) {
            checkQueue()
            return
        }
        restoreQueue()
        loadMatch()
    }

    /**
     * Picks up a search started before the app was closed
     */
    suspend fun restoreQueue() {
        val format = repository.queuedFormat().getOrNull() ?: return
        _uiState.update { it.copy(isSearching = true, format = format, nearby = null) }
    }

    /**
     * Shows the players searching near this one on the queue card's map, as the backend pushes them: when the
     * stream opens, then whenever anyone joins or leaves the queue. Nothing is polled. Reconnects after the
     * connection drops, keeping the last dots meanwhile; a refused stream leaves the map as it is
     */
    suspend fun followQueue() {
        repository.queueUpdates()
            .reconnecting { }
            .collect { update ->
                // Not once the player was matched or left: the map is gone, and the match comes from followMatches
                if (update is QueueUpdate.Nearby && _uiState.value.isSearching) {
                    _uiState.update { it.copy(nearby = update.players) }
                }
            }
    }

    /**
     * Follows the user's own games live, refreshing the queue and the match whenever they change: matchmaking
     * creating the match, or a player accepting or cancelling it. The backend sends them first on every connect,
     * so a match found while the stream was down is caught up. Reconnects after the connection drops; a refused
     * stream is left to the pushes and the app coming back
     */
    suspend fun followMatches() {
        gameListUpdates.updates()
            .filterIsInstance<GameListUpdate.Mine>()
            .reconnecting { }
            .collect { refresh() }
    }

    /**
     * Shows the player's matched game that is still waiting for answers, e.g. after the app was closed,
     * unless they already left its pop-up with "Go to game"
     */
    suspend fun loadMatch() {
        val match = gamesRepository.getMyGames().getOrNull()?.pendingMatch() ?: return
        if (match.id == leftMatchId) {
            return
        }
        // A pending match means matchmaking already took the player out of the queue
        _uiState.update { it.copy(match = match, isSearching = false, errorMessage = null) }
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

        // Already accepted is what the player asked for, so it is not an error
        val error = gamesRepository.acceptMatch(match.id).exceptionOrNull()?.takeUnless { it.isAlreadyAccepted() }
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
        leftMatchId = _uiState.value.match?.id
        _uiState.update { it.copy(match = null, errorMessage = null) }
    }

    fun onMatchFellThroughSeen() {
        _uiState.update { it.copy(matchFellThrough = false) }
    }
}

private fun List<Game>.pendingMatch(): Game? =
    firstOrNull { it.source == GameSource.MATCHED && it.status == GameStatus.OPEN }

private fun Throwable.isAlreadyAccepted(): Boolean =
    this is HttpException && code() == HttpURLConnection.HTTP_CONFLICT && toUserMessage() == ALREADY_ACCEPTED_ERROR

// A refused match that isn't cancelled is past the cutoff: the deadline job cancels it within a minute
private fun acceptFailedMessage(error: Throwable): String {
    if (error is HttpException && error.code() == HttpURLConnection.HTTP_CONFLICT) {
        return ACCEPT_TOO_LATE_MESSAGE
    }
    return ACCEPT_FAILED_MESSAGE
}
