package com.example.cpen321application.feature.gamedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.repository.CourtsRepository
import com.example.cpen321application.core.data.repository.FriendsRepository
import com.example.cpen321application.core.data.repository.GamesRepository
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * One game's screen: its details, and joining, leaving, changing, scoring or cancelling it
 * @param gameId the game on screen
 * @param gamesRepository the /games routes
 * @param courtsRepository the /courts route, for changing the court
 * @param currentUsername the signed-in user's username, null if signed out
 * @param friendsRepository the /friends routes, for the friends to invite
 * @param session sends the friends requests as the signed-in user
 */
class GameDetailViewModel(
    private val gameId: String,
    private val gamesRepository: GamesRepository,
    private val courtsRepository: CourtsRepository,
    private val currentUsername: () -> String?,
    private val friendsRepository: FriendsRepository,
    private val session: AuthSession
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            reload()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun join(team: Int) = sendAction { gamesRepository.joinGame(gameId, team) }

    fun leave() = sendAction { gamesRepository.leaveGame(gameId) }

    fun changeTime(startsAt: Instant) = sendAction { gamesRepository.changeGameTime(gameId, startsAt) }

    fun changeCourt(courtId: Int) = sendAction { gamesRepository.changeGameLocation(gameId, courtId) }

    /**
     * @param rounds the (team 1, team 2) score of each set, already checked with scoreError
     * @param onReported called once the backend saved the score, to close the score form
     */
    fun reportScore(rounds: List<Pair<Int, Int>>, onReported: () -> Unit) =
        sendAction(onSuccess = onReported) { gamesRepository.reportScore(gameId, rounds) }

    /**
     * Cancels the game for every player
     * @param onCancelled called once the game is cancelled, to leave its screen
     */
    fun cancel(onCancelled: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            gamesRepository.cancelGame(gameId)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                    onCancelled()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.toUserMessage()) }
                }
        }
    }

    /**
     * Loads the courts for the court picker, unless they already loaded
     */
    fun loadCourts() {
        if (_uiState.value.courts.isNotEmpty() || _uiState.value.isLoadingCourts) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCourts = true, courtsErrorMessage = null) }
            courtsRepository.getCourts()
                .onSuccess { courts ->
                    _uiState.update { it.copy(isLoadingCourts = false, courts = courts) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoadingCourts = false, courtsErrorMessage = error.toUserMessage()) }
                }
        }
    }

    /**
     * Loads the friends for the invite dialog, unless they already loaded
     */
    fun loadFriends() {
        val invite = _uiState.value.invite
        if (invite.friends != null || invite.isLoadingFriends) {
            return
        }

        viewModelScope.launch {
            updateInvite { it.copy(isLoadingFriends = true, friendsErrorMessage = null) }
            friendsRepository.getFriends(session)
                .onSuccess { friends ->
                    updateInvite { it.copy(isLoadingFriends = false, friends = friends) }
                }
                .onFailure { error ->
                    updateInvite { it.copy(isLoadingFriends = false, friendsErrorMessage = error.toUserMessage()) }
                }
        }
    }

    /**
     * Sends a friend a push notification inviting them to the game
     * @param userId the friend's user ID
     */
    fun invite(userId: String) {
        if (_uiState.value.invite.invitingUserId != null) {
            return
        }

        viewModelScope.launch {
            updateInvite { it.copy(invitingUserId = userId, errorMessage = null) }
            gamesRepository.inviteFriend(gameId, userId)
                .onSuccess { notified ->
                    updateInvite { it.copy(invitingUserId = null, invited = it.invited + (userId to notified)) }
                }
                .onFailure { error ->
                    updateInvite { it.copy(invitingUserId = null, errorMessage = error.toUserMessage()) }
                }
        }
    }

    private fun updateInvite(change: (InviteUiState) -> InviteUiState) {
        _uiState.update { it.copy(invite = change(it.invite)) }
    }

    // Sends an action, then reloads the game so the screen shows what the backend saved
    private fun sendAction(onSuccess: () -> Unit = {}, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            action()
                .onSuccess {
                    onSuccess()
                    reload()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
                }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private suspend fun reload() {
        gamesRepository.getGame(gameId)
            .onSuccess { game ->
                val actions = gameActions(game, currentUsername(), Instant.now())
                _uiState.update { it.copy(game = game, actions = actions) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
            }
    }
}
