package com.example.cpen321application.feature.tournamentdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.data.repository.TournamentsRepository
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * One tournament's screen: its registrants or bracket, and joining, leaving or cancelling it
 * @param tournamentId the tournament on screen
 * @param tournamentsRepository the /tournaments routes
 * @param currentUsername the signed-in user's username, null if signed out
 */
class TournamentDetailViewModel(
    private val tournamentId: String,
    private val tournamentsRepository: TournamentsRepository,
    private val currentUsername: () -> String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentDetailUiState())
    val uiState: StateFlow<TournamentDetailUiState> = _uiState.asStateFlow()

    /**
     * Loads the tournament. Called every time the screen is shown,
     * so the bracket is up to date after a score was reported on a match's screen.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            reload()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun join() = sendAction { tournamentsRepository.joinTournament(tournamentId) }

    fun leave() = sendAction { tournamentsRepository.leaveTournament(tournamentId) }

    /**
     * Cancels the tournament for every player
     * @param onCancelled called once the tournament is cancelled, to leave its screen
     */
    fun cancel(onCancelled: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            tournamentsRepository.cancelTournament(tournamentId)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                    onCancelled()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.toUserMessage()) }
                }
        }
    }

    private fun sendAction(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            action()
                .onSuccess { reload() }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
                }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private suspend fun reload() {
        tournamentsRepository.getTournament(tournamentId)
            .onSuccess { tournament ->
                val actions = tournamentActions(tournament, currentUsername(), Instant.now())
                _uiState.update { it.copy(tournament = tournament, actions = actions) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
            }
    }
}
