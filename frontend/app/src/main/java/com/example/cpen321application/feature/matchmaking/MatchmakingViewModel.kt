package com.example.cpen321application.feature.matchmaking

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val PERMISSION_DENIED_MESSAGE = "Location permission denied. Enter an address instead."

/**
 * Holds the location the user picked on the Matchmaking tab.
 * The screen fetches the location (GPS or Places) and reports the result here.
 */
class MatchmakingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MatchmakingUiState())
    val uiState: StateFlow<MatchmakingUiState> = _uiState.asStateFlow()

    /**
     * Switches between current location and address, dropping the location and any
     * lookup still running for the other option
     */
    fun onOptionSelected(option: LocationOption) {
        _uiState.update {
            it.copy(
                selectedOption = option,
                location = null,
                errorMessage = null,
                isLoading = false,
                isSearching = false
            )
        }
    }

    /**
     * Picks singles, doubles or either. Stops a search started with the old format.
     */
    fun onFormatSelected(format: MatchFormat) {
        _uiState.update { it.copy(format = format, isSearching = false) }
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
     * Starts searching near the picked location. Does nothing without one, while it is
     * still loading, or while a search is already running.
     */
    fun onFindMatch() {
        if (!_uiState.value.canFindMatch) {
            return
        }
        _uiState.update { it.copy(isSearching = true) }
    }
}
