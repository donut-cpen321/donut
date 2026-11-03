package com.example.cpen321application.feature.matchsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.model.MatchResult
import com.example.cpen321application.core.data.model.resultFor
import com.example.cpen321application.core.data.repository.FriendsRepository
import com.example.cpen321application.core.data.repository.GamesRepository
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val HTTP_CONFLICT = 409

/**
 * A completed game's summary: the result, the score, everyone's rating change, and friend requests to the other players
 * @param gameId the completed game on screen
 * @param gamesRepository the /games routes
 * @param friendsRepository the /friends routes
 * @param session sends the friends requests as the signed-in user
 * @param currentUsername the signed-in user's username, null if signed out
 */
class MatchSummaryViewModel(
    private val gameId: String,
    private val gamesRepository: GamesRepository,
    private val friendsRepository: FriendsRepository,
    private val session: AuthSession,
    private val currentUsername: () -> String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchSummaryUiState())
    val uiState: StateFlow<MatchSummaryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            gamesRepository.getGame(gameId)
                .onSuccess { game ->
                    val match = game.resultFor(currentUsername())
                    _uiState.update {
                        it.copy(
                            match = match,
                            errorMessage = if (match == null) "You did not play in this game." else null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
                }
            _uiState.value.match?.let { loadFriendStatuses(it) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Sends a friend request to another player of the match, or accepts theirs if they already sent one
     * @param userId the player to befriend
     */
    fun sendFriendRequest(userId: String) {
        val state = _uiState.value
        if (userId in state.sendingRequestTo) return
        val wasRequested = state.friendStatuses[userId] == FriendStatus.REQUEST_RECEIVED

        viewModelScope.launch {
            _uiState.update {
                it.copy(sendingRequestTo = it.sendingRequestTo + userId, friendMessage = null, friendErrorMessage = null)
            }
            friendsRepository.sendFriendRequest(session, userId)
                .onSuccess {
                    val status = if (wasRequested) FriendStatus.FRIENDS else FriendStatus.REQUEST_SENT
                    _uiState.update {
                        it.copy(
                            friendStatuses = it.friendStatuses + (userId to status),
                            friendMessage = if (wasRequested) "You are now friends" else "Friend request sent"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(friendErrorMessage = error.toUserMessage()) }
                    // Already friends or already requested: show where things actually stand
                    if (error is HttpException && error.code() == HTTP_CONFLICT) {
                        _uiState.value.match?.let { loadFriendStatuses(it) }
                    }
                }
            _uiState.update { it.copy(sendingRequestTo = it.sendingRequestTo - userId) }
        }
    }

    fun clearFriendMessages() {
        _uiState.update { it.copy(friendMessage = null, friendErrorMessage = null) }
    }

    // A list that fails to load counts as empty, so its players can still be sent a request
    private suspend fun loadFriendStatuses(match: MatchResult) {
        val friends = friendsRepository.getFriends(session).getOrNull().orEmpty().map { it.userId }.toSet()
        val received = friendsRepository.getFriendRequests(session).getOrNull().orEmpty().map { it.userId }.toSet()
        val sent = friendsRepository.getOutgoingFriendRequests(session).getOrNull().orEmpty().map { it.userId }.toSet()

        val statuses = match.game.players
            .filter { it.id != match.me.id }
            .associate { player ->
                player.id to when (player.id) {
                    in friends -> FriendStatus.FRIENDS
                    in sent -> FriendStatus.REQUEST_SENT
                    in received -> FriendStatus.REQUEST_RECEIVED
                    else -> FriendStatus.NOT_FRIENDS
                }
            }
        _uiState.update { it.copy(friendStatuses = statuses) }
    }
}
