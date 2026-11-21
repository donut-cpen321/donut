package com.donut.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.resultFor
import com.donut.app.core.data.repository.CourtsRepository
import com.donut.app.core.data.repository.CourtsRepositoryImpl
import com.donut.app.core.data.repository.EquipmentRepository
import com.donut.app.core.data.repository.EquipmentRepositoryImpl
import com.donut.app.core.data.repository.FriendsRepository
import com.donut.app.core.data.repository.FriendsRepositoryImpl
import com.donut.app.core.data.repository.FriendsPageUpdate
import com.donut.app.core.data.repository.FriendsPageUpdates
import com.donut.app.core.data.repository.FriendsPageUpdatesImpl
import com.donut.app.core.data.repository.reconnecting
import com.donut.app.core.network.toUserMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: FriendsRepository = FriendsRepositoryImpl(),
    private val equipmentRepository: EquipmentRepository = EquipmentRepositoryImpl(),
    private val courtsRepository: CourtsRepository = CourtsRepositoryImpl(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val friendsPageUpdates: FriendsPageUpdates = FriendsPageUpdatesImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // The Equipment tab, which shares this state
    val equipment = ProfileEquipment(_uiState, viewModelScope, ioDispatcher, equipmentRepository)

    /**
     * Shows the friends list and friend requests as the backend pushes them, e.g. when a friend starts or stops
     * playing, until the caller stops collecting. The Friends tab calls it only while it is on screen, so nothing
     * is fetched or kept open for friends elsewhere. Reconnects after the connection drops; the backend sends the
     * current page on every connect, so changes made while disconnected aren't missed
     * @param session the signed-in user, whose friends page it is
     */
    suspend fun followFriendsPage(session: AuthSession) {
        friendsPageUpdates.updates(session)
            .reconnecting { error ->
                // The backend refused the stream: the page keeps what it last loaded
                _uiState.update {
                    it.copy(
                        isLoadingFriends = false,
                        isLoadingRequests = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
            .flowOn(ioDispatcher)
            .collect { update ->
                when (update) {
                    is FriendsPageUpdate.Friends -> _uiState.update {
                        it.copy(friends = update.friends, isLoadingFriends = false)
                    }
                    is FriendsPageUpdate.Requests -> _uiState.update {
                        it.copy(friendRequests = update.incoming, isLoadingRequests = false)
                    }
                }
            }
    }

    fun initSession(session: AuthSession?) {
        if (session == null) return
        val currentUsername = session.user?.username.orEmpty()
        _uiState.update { it.copy(username = currentUsername) }
        fetchUserMe(session)
        fetchMatchHistory(session)
        equipment.fetchEquipment(session)
        fetchPreferredCourts(session)
        fetchAllCourts(session)
        if (_uiState.value.selectedTab == ProfileTab.FRIENDS) {
            // The Friends tab opens the friends page's event stream, which sends the friends and requests first,
            // so loading them here too would fetch them twice
            _uiState.update { it.copy(isLoadingFriends = true, isLoadingRequests = true) }
        } else {
            fetchFriends(session)
            fetchFriendRequests(session)
        }
    }

    fun selectTab(tab: ProfileTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadAll(session: AuthSession) {
        fetchUserMe(session)
        fetchFriends(session)
        fetchFriendRequests(session)
        fetchMatchHistory(session)
        equipment.fetchEquipment(session)
        fetchPreferredCourts(session)
        fetchAllCourts(session)
    }

    fun fetchUserMe(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingUserMe = true) }
            val result = repository.getUserMe(session)
            result.onSuccess { userMe ->
                _uiState.update { state ->
                    val newElo = userMe.elo ?: state.elo
                    val newUsername = if (!userMe.username.isNullOrBlank()) userMe.username else state.username
                    state.copy(
                        elo = newElo,
                        username = newUsername,
                        name = userMe.name.orEmpty().ifBlank { state.name },
                        avatarUrl = userMe.avatarUrl ?: state.avatarUrl,
                        isLoadingUserMe = false,
                    )
                }
                val userId = userMe.id
                if (!userId.isNullOrBlank()) {
                    fetchUserPhoto(session, userId)
                }
            }.onFailure { error ->
                // Said, not swallowed: otherwise the rating card silently keeps an old or missing rating
                _uiState.update { state ->
                    state.copy(isLoadingUserMe = false, errorMessage = error.toUserMessage())
                }
            }
        }
    }

    fun fetchUserPhoto(session: AuthSession, userId: String) {
        viewModelScope.launch(ioDispatcher) {
            val result = repository.getUserPhoto(session, userId)
            result.onSuccess { photoUrl ->
                if (!photoUrl.isNullOrBlank()) {
                    _uiState.update { it.copy(avatarUrl = photoUrl) }
                }
            }
        }
    }

    fun fetchFriends(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
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
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun fetchFriendRequests(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
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
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun fetchMatchHistory(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingMatches = true, errorMessage = null) }
            val result = repository.getMatchHistory(session)
            result.onSuccess { games ->
                val username = session.user?.username
                _uiState.update {
                    it.copy(
                        matchHistory = games.mapNotNull { game -> game.resultFor(username) },
                        isLoadingMatches = false,
                    )
                }
            }.onFailure { error ->
                // Keeps what was loaded and says why, instead of an empty history and a 0–0 record that look
                // like the user has never played
                _uiState.update {
                    it.copy(
                        isLoadingMatches = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun fetchPreferredCourts(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingPreferredCourts = true) }
            val result = courtsRepository.getPreferredCourts(session)
            result.onSuccess { courts ->
                _uiState.update {
                    it.copy(
                        preferredCourts = courts,
                        isLoadingPreferredCourts = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingPreferredCourts = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun fetchAllCourts(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingAllCourts = true) }
            val result = courtsRepository.getCourts(session)
            result.onSuccess { courts ->
                _uiState.update {
                    it.copy(
                        allCourts = courts,
                        isLoadingAllCourts = false,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingAllCourts = false) }
            }
        }
    }

    fun removeFriend(session: AuthSession, userId: String) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = repository.removeFriend(session, userId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Friend removed",
                    )
                }
                // No reload: the friends page's event stream pushes the new list
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun acceptFriendRequest(session: AuthSession, userId: String) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = repository.acceptFriendRequest(session, userId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Friend request accepted",
                    )
                }
                // No reload: the friends page's event stream pushes the new friends and requests
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun openAddPreferredCourtDialog(session: AuthSession) {
        _uiState.update { it.copy(showAddCourtDialog = true) }
        fetchAllCourts(session)
    }

    fun closeAddPreferredCourtDialog() {
        _uiState.update { it.copy(showAddCourtDialog = false) }
    }

    fun addPreferredCourt(session: AuthSession, court: Court) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = courtsRepository.addPreferredCourt(session, court.id)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        showAddCourtDialog = false,
                        successMessage = "Preferred court added",
                    )
                }
                fetchPreferredCourts(session)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun deletePreferredCourt(session: AuthSession, courtId: Int) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = courtsRepository.removePreferredCourt(session, courtId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Preferred court removed",
                    )
                }
                fetchPreferredCourts(session)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
