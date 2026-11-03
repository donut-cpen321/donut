package com.example.cpen321application.feature.profile

import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.network.dto.MatchDto

enum class ProfileTab {
    MATCH_HISTORY,
    FRIENDS,
    EQUIPMENT,
}

data class ProfileUiState(
    val username: String = "",
    val elo: Int = 1500,
    val selectedTab: ProfileTab = ProfileTab.FRIENDS,
    val friends: List<FriendDto> = emptyList(),
    val friendRequests: List<FriendRequestDto> = emptyList(),
    val matchHistory: List<MatchDto> = emptyList(),
    val equipment: List<EquipmentDto> = emptyList(),
    val isLoadingUserMe: Boolean = false,
    val isLoadingFriends: Boolean = false,
    val isLoadingRequests: Boolean = false,
    val isLoadingMatches: Boolean = false,
    val isLoadingEquipment: Boolean = false,
    val isActionInProgress: Boolean = false,
    val showEquipmentDialog: Boolean = false,
    val editingEquipment: EquipmentDto? = null,
    val equipmentNameInput: String = "",
    val equipmentTypeInput: EquipmentType = EquipmentType.RACKET,
    val equipmentIsDefaultInput: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val friendCount: Int get() = friends.size
    val isLoadingAny: Boolean
        get() = isLoadingUserMe || isLoadingFriends || isLoadingRequests || isLoadingMatches || isLoadingEquipment
}
