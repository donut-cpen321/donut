package com.example.cpen321application.feature.profile

import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.data.model.MatchOutcome
import com.example.cpen321application.core.data.model.MatchResult

enum class ProfileTab {
    MATCH_HISTORY,
    FRIENDS,
    EQUIPMENT,
}

data class ProfileUiState(
    val username: String = "",
    val name: String = "",  // the real name, empty until the profile loads
    val elo: Int = 1500,
    val selectedTab: ProfileTab = ProfileTab.FRIENDS,
    val friends: List<FriendDto> = emptyList(),
    val friendRequests: List<FriendRequestDto> = emptyList(),
    val matchHistory: List<MatchResult> = emptyList(),  // most recent first
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

/**
 * @return e.g. "12–7": wins, then losses, of the matches with a winner
 */
fun recordText(matchHistory: List<MatchResult>): String {
    val wins = matchHistory.count { it.outcome == MatchOutcome.WIN }
    val losses = matchHistory.count { it.outcome == MatchOutcome.LOSS }
    return "$wins–$losses"
}

/**
 * A "Donut set!": the user won the match and took a set 6–0
 */
fun hasDonutSet(match: MatchResult): Boolean =
    match.outcome == MatchOutcome.WIN && match.myScores.any { (mine, theirs) -> mine == DONUT_SET_GAMES && theirs == 0 }

private const val DONUT_SET_GAMES = 6
