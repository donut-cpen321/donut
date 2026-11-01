package com.example.cpen321application.feature.profile

import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.network.dto.MatchDto

enum class ProfileTab {
    MATCH_HISTORY,
    FRIENDS,
}

data class ProfileUiState(
    val username: String = "",
    val elo: Int = 1500, // starting rating; TODO: get from GET /user/me
    val selectedTab: ProfileTab = ProfileTab.FRIENDS,
    val friends: List<FriendDto> = emptyList(),
    val friendRequests: List<FriendRequestDto> = emptyList(),
    val matchHistory: List<MatchDto> = emptyList(),
    val isLoadingFriends: Boolean = false,
    val isLoadingRequests: Boolean = false,
    val isLoadingMatches: Boolean = false,
    val isActionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val friendCount: Int get() = friends.size
    val isLoadingAny: Boolean get() = isLoadingFriends || isLoadingRequests || isLoadingMatches
}
