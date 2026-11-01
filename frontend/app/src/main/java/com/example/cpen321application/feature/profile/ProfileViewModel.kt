package com.example.cpen321application.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.repository.FriendsRepository
import com.example.cpen321application.core.data.repository.FriendsRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: FriendsRepository = FriendsRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun initSession(session: AuthSession?) {
        if (session == null) return
        val currentUsername = session.user?.username.orEmpty()
        _uiState.update { it.copy(username = currentUsername) }
        loadAll(session)
    }

    fun selectTab(tab: ProfileTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadAll(session: AuthSession) {
        fetchFriends(session)
        fetchFriendRequests(session)
        fetchMatchHistory(session)
    }

    fun fetchFriends(session: AuthSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFriends = true, errorMessage = null) }
            val result = repository.getFriends(session)
            result.onSuccess { friendsList ->
                _uiState.update {
                    it.copy(
                        friends = friendsList,
                        isLoadingFriends = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingFriends = false,
                        errorMessage = error.localizedMessage ?: "Failed to load friends",
                    )
                }
            }
        }
    }

    fun fetchFriendRequests(session: AuthSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRequests = true, errorMessage = null) }
            val result = repository.getFriendRequests(session)
            result.onSuccess { requestsList ->
                _uiState.update {
                    it.copy(
                        friendRequests = requestsList,
                        isLoadingRequests = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingRequests = false,
                        errorMessage = error.localizedMessage ?: "Failed to load friend requests",
                    )
                }
            }
        }
    }

    fun fetchMatchHistory(session: AuthSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMatches = true, errorMessage = null) }
            val result = repository.getMatchHistory(session)
            result.onSuccess { matches ->
                _uiState.update {
                    it.copy(
                        matchHistory = matches,
                        isLoadingMatches = false,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoadingMatches = false,
                        matchHistory = emptyList(),
                    )
                }
            }
        }
    }

    fun removeFriend(session: AuthSession, userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = repository.removeFriend(session, userId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Friend removed",
                    )
                }
                fetchFriends(session)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.localizedMessage ?: "Failed to remove friend",
                    )
                }
            }
        }
    }

    fun acceptFriendRequest(session: AuthSession, userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = repository.acceptFriendRequest(session, userId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Friend request accepted",
                    )
                }
                fetchFriends(session)
                fetchFriendRequests(session)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.localizedMessage ?: "Failed to accept friend request",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
