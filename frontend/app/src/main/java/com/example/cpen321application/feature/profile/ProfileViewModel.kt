package com.example.cpen321application.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.repository.EquipmentRepository
import com.example.cpen321application.core.data.repository.EquipmentRepositoryImpl
import com.example.cpen321application.core.data.repository.FriendsRepository
import com.example.cpen321application.core.data.repository.FriendsRepositoryImpl
import com.example.cpen321application.core.network.dto.CreateEquipmentRequestDto
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.network.dto.UpdateEquipmentRequestDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ProfileViewModel(
    private val repository: FriendsRepository = FriendsRepositoryImpl(),
    private val equipmentRepository: EquipmentRepository = EquipmentRepositoryImpl(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
        fetchUserMe(session)
        fetchFriends(session)
        fetchFriendRequests(session)
        fetchMatchHistory(session)
        fetchEquipment(session)
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
                        isLoadingUserMe = false,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(isLoadingUserMe = false)
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
                        errorMessage = error.localizedMessage ?: "Failed to load friends",
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
                        errorMessage = error.localizedMessage ?: "Failed to load friend requests",
                    )
                }
            }
        }
    }

    fun fetchMatchHistory(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
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

    fun fetchEquipment(session: AuthSession) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingEquipment = true) }
            val result = equipmentRepository.getEquipment(session)
            result.onSuccess { equipmentList ->
                _uiState.update {
                    it.copy(
                        equipment = equipmentList,
                        isLoadingEquipment = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingEquipment = false,
                        errorMessage = error.localizedMessage ?: "Failed to load equipment",
                    )
                }
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

    fun openAddEquipmentDialog() {
        _uiState.update {
            it.copy(
                showEquipmentDialog = true,
                editingEquipment = null,
                equipmentNameInput = "",
                equipmentTypeInput = EquipmentType.RACKET,
                equipmentIsDefaultInput = false,
            )
        }
    }

    fun openEditEquipmentDialog(equipment: EquipmentDto) {
        val type = equipment.equipmentType
        val name = if (type == EquipmentType.BALLS) "Tennis Balls" else equipment.name
        _uiState.update {
            it.copy(
                showEquipmentDialog = true,
                editingEquipment = equipment,
                equipmentNameInput = name,
                equipmentTypeInput = type,
                equipmentIsDefaultInput = equipment.isDefault,
            )
        }
    }

    fun closeEquipmentDialog() {
        _uiState.update {
            it.copy(
                showEquipmentDialog = false,
                editingEquipment = null,
                equipmentNameInput = "",
            )
        }
    }

    fun updateEquipmentInputs(name: String, type: EquipmentType, isDefault: Boolean) {
        val effectiveName = if (type == EquipmentType.BALLS) "Tennis Balls" else name
        _uiState.update {
            it.copy(
                equipmentNameInput = effectiveName,
                equipmentTypeInput = type,
                equipmentIsDefaultInput = isDefault,
            )
        }
    }

    fun saveEquipment(session: AuthSession) {
        val state = _uiState.value
        val type = state.equipmentTypeInput
        val name = if (type == EquipmentType.BALLS) "Tennis Balls" else state.equipmentNameInput.trim()

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Equipment name cannot be empty") }
            return
        }

        val isDefault = state.equipmentIsDefaultInput
        val editing = state.editingEquipment
        val nextMaintenanceAt = nextMaintenanceFor(type)

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            if (editing == null) {
                val request = CreateEquipmentRequestDto(
                    type = type.name,
                    name = name,
                    isDefault = isDefault,
                    nextMaintenanceAt = nextMaintenanceAt,
                )
                val result = equipmentRepository.createEquipment(session, request)
                handleEquipmentSaveResult(session, result, "Equipment added", "Failed to add equipment")
            } else {
                val request = UpdateEquipmentRequestDto(
                    type = type.name,
                    name = name,
                    isDefault = isDefault,
                    nextMaintenanceAt = nextMaintenanceAt,
                )
                val result = equipmentRepository.updateEquipment(session, editing.id, request)
                handleEquipmentSaveResult(session, result, "Equipment updated", "Failed to update equipment")
            }
        }
    }

    // Only tennis rackets should have a next maintenance date, set to 1 month from now
    private fun nextMaintenanceFor(type: EquipmentType): String? {
        if (type != EquipmentType.RACKET) return null
        val nextMonth = Instant.now().plus(30, ChronoUnit.DAYS)
        return DateTimeFormatter.ISO_INSTANT.format(nextMonth)
    }

    private fun handleEquipmentSaveResult(
        session: AuthSession,
        result: Result<*>,
        successMessage: String,
        failureMessage: String,
    ) {
        result.onSuccess {
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    showEquipmentDialog = false,
                    successMessage = successMessage,
                )
            }
            fetchEquipment(session)
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    errorMessage = error.localizedMessage ?: failureMessage,
                )
            }
        }
    }

    fun deleteEquipment(session: AuthSession, equipmentId: Int) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = equipmentRepository.deleteEquipment(session, equipmentId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Equipment removed",
                    )
                }
                fetchEquipment(session)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.localizedMessage ?: "Failed to remove equipment",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}