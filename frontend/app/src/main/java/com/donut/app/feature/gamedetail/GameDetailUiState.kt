package com.donut.app.feature.gamedetail

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Game
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.FriendDto

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
    val invite: InviteUiState = InviteUiState(),

    // For equipment selection
    val userEquipment: List<EquipmentDto> = emptyList(),
    val selectedEquipmentIds: Set<Int> = emptySet(),
    val isLoadingEquipment: Boolean = false,
    val equipmentErrorMessage: String? = null
)

data class InviteUiState(
    val friends: List<FriendDto>? = null,  // null until loaded
    val isLoadingFriends: Boolean = false,
    val friendsErrorMessage: String? = null,
    val invitingUserId: String? = null,  // the friend whose invite has no answer yet
    val invited: Map<String, Boolean> = emptyMap(),  // friend's user ID to whether their device was notified
    val errorMessage: String? = null
)
