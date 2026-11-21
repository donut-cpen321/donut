package com.donut.app.feature.tournamentdetail
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.repository.FriendsRepository
import com.donut.app.core.data.repository.FriendsRepositoryImpl
import com.donut.app.core.data.repository.TournamentsRepository
import com.donut.app.core.data.repository.reconnecting
import com.donut.app.core.data.repository.safeLog
import com.donut.app.core.network.toUserMessage
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
 * @param session the user's AuthSession for fetching missing player photos
 * @param friendsRepository repository for user photo lookups
 */
class TournamentDetailViewModel(
    private val tournamentId: String,
    private val tournamentsRepository: TournamentsRepository,
    private val currentUsername: () -> String?,
    private val session: AuthSession? = null,
    private val friendsRepository: FriendsRepository = FriendsRepositoryImpl()
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentDetailUiState())
    val uiState: StateFlow<TournamentDetailUiState> = _uiState.asStateFlow()

    init {
        // The screen opens the tournament's event stream (followUpdates), which sends the tournament first,
        // so it isn't loaded here too
        _uiState.update { it.copy(isLoading = true) }
    }

    /**
     * Loads the tournament again, for Retry after an error
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

    /**
     * Shows each change to the tournament as the backend pushes it (players joining or leaving, the bracket being
     * drawn, matches started or scored), until the caller stops collecting. Reconnects after the connection drops;
     * the backend sends the current tournament on every connect, so changes made while disconnected aren't missed
     */
    suspend fun followUpdates() {
        tournamentsRepository.tournamentUpdates(tournamentId)
            .reconnecting { error ->
                // The backend refused the stream, e.g. the tournament is gone: the screen keeps what it showed
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
            }
            .collect { tournament ->
                showTournament(tournament)
                _uiState.update { it.copy(isLoading = false) }
            }
    }

    private fun showTournament(tournament: Tournament) {
        val actions = tournamentActions(tournament, currentUsername(), Instant.now())
        _uiState.update { it.copy(tournament = tournament, actions = actions) }
        enrichMissingPhotos(tournament)
    }

    private fun enrichMissingPhotos(tournament: Tournament) {
        val sess = session ?: return
        val missingPlayers = tournament.players.filter { it.photoUrl.isNullOrBlank() }
        val missingCreator = tournament.creator.photoUrl.isNullOrBlank()

        if (missingPlayers.isEmpty() && !missingCreator) return

        viewModelScope.launch {
            var updatedPlayers = tournament.players
            var updatedCreator = tournament.creator
            var changed = false

            for (player in missingPlayers) {
                val result = friendsRepository.getUserPhoto(sess, player.id)
                result.onSuccess { photoUrl ->
                    if (!photoUrl.isNullOrBlank()) {
                        safeLog("TournamentDetail", "Fetched photoUrl for player ${player.name} (${player.id}): $photoUrl")
                        updatedPlayers = updatedPlayers.map { p ->
                            if (p.id == player.id) p.copy(photoUrl = photoUrl) else p
                        }
                        changed = true
                    } else {
                        safeLog("TournamentDetail", "No photoUrl for player ${player.name} (${player.id})")
                    }
                }.onFailure { err ->
                    safeLog("TournamentDetail", "Failed to fetch photoUrl for ${player.name}: ${err.message}")
                }
            }

            if (missingCreator) {
                val creatorResult = friendsRepository.getUserPhoto(sess, tournament.creator.id)
                creatorResult.onSuccess { photoUrl ->
                    if (!photoUrl.isNullOrBlank()) {
                        com.donut.app.core.data.repository.safeLog("TournamentDetail", "Fetched photoUrl for creator ${tournament.creator.name}: $photoUrl")
                        updatedCreator = updatedCreator.copy(photoUrl = photoUrl)
                        changed = true
                    }
                }
            }

            if (changed) {
                val updatedTournament = tournament.copy(
                    players = updatedPlayers,
                    creator = updatedCreator
                )
                val actions = tournamentActions(updatedTournament, currentUsername(), Instant.now())
                _uiState.update { it.copy(tournament = updatedTournament, actions = actions) }
            }
        }
    }

    private suspend fun reload() {
        tournamentsRepository.getTournament(tournamentId)
            .onSuccess(::showTournament)
            .onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
            }
    }
}
