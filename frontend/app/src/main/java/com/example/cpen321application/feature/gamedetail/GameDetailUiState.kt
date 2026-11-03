package com.example.cpen321application.feature.gamedetail

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.network.dto.FriendDto

data class GameDetailUiState(
    val game: Game? = null,
    val actions: GameActions? = null,  // set together with game
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,  // an action was sent and has no answer yet
    val errorMessage: String? = null,

    // For changing the court, loaded when the court picker opens
    val courts: List<Court> = emptyList(),
    val isLoadingCourts: Boolean = false,
    val courtsErrorMessage: String? = null,

    // For inviting friends, loaded when the invite dialog opens
    val invite: InviteUiState = InviteUiState()
)

data class InviteUiState(
    val friends: List<FriendDto>? = null,  // null until loaded
    val isLoadingFriends: Boolean = false,
    val friendsErrorMessage: String? = null,
    val invitingUserId: String? = null,  // the friend whose invite has no answer yet
    val invited: Map<String, Boolean> = emptyMap(),  // friend's user ID to whether their device was notified
    val errorMessage: String? = null
)
