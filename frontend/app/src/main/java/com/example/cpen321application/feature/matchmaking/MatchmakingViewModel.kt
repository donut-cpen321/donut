package com.example.cpen321application.feature.matchmaking

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val PERMISSION_DENIED_MESSAGE = "Location permission denied. Enter an address instead."
const val JOIN_FAILED_MESSAGE = "Could not join the queue. Check your connection and try again."
const val LEAVE_FAILED_MESSAGE = "Could not leave the queue. Check your connection and try again."

/**
 * Holds the location and format the user picked on the Matchmaking tab, and their place in the queue.
 * The screen fetches the location (GPS or Places) and reports the result here.
 * Queue calls are suspend functions the screen launches, so the screen also runs the 10-second check.
 */
class MatchmakingViewModel(private val repository: MatchmakingRepository) : ViewModel() {
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

        val joined = repository.joinQueue(state.format, location).isSuccess
        if (joined) {
            _uiState.update { it.copy(isUpdatingQueue = false, isSearching = true) }
        } else {
            _uiState.update { it.copy(isUpdatingQueue = false, errorMessage = JOIN_FAILED_MESSAGE) }
        }
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
     * Asks the backend whether the player is still waiting. Once they are not, matchmaking found them a game.
     * A failed check changes nothing, the next one tries again.
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
        _uiState.update { it.copy(isSearching = false, matchFound = true) }
    }

    /**
     * Picks up a search started before the app was closed
     */
    suspend fun restoreQueue() {
        val format = repository.queuedFormat().getOrNull() ?: return
        _uiState.update { it.copy(isSearching = true, format = format) }
    }

    fun onMatchFoundSeen() {
        _uiState.update { it.copy(matchFound = false) }
    }
}
