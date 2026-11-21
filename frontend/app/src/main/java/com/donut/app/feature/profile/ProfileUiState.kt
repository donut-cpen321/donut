package com.donut.app.feature.profile

import com.donut.app.core.data.model.Court
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.EquipmentType
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.donut.app.core.data.model.MatchOutcome
import com.donut.app.core.data.model.MatchResult
import kotlin.math.roundToInt

enum class ProfileTab {
    MATCH_HISTORY,
    FRIENDS,
    EQUIPMENT,
    PREFERRED_COURTS,
}

data class ProfileUiState(
    val username: String = "",
    val name: String = "",  // the real name, empty until the profile loads
    val avatarUrl: String? = null,
    val elo: Int = 1500,
    val selectedTab: ProfileTab = ProfileTab.FRIENDS,
    val friends: List<FriendDto> = emptyList(),
    val friendRequests: List<FriendRequestDto> = emptyList(),
    val matchHistory: List<MatchResult> = emptyList(),  // most recent first
    val equipment: List<EquipmentDto> = emptyList(),
    val preferredCourts: List<Court> = emptyList(),
    val allCourts: List<Court> = emptyList(),
    val isLoadingUserMe: Boolean = false,
    val isLoadingFriends: Boolean = false,
    val isLoadingRequests: Boolean = false,
    val isLoadingMatches: Boolean = false,
    val isLoadingEquipment: Boolean = false,
    val isLoadingPreferredCourts: Boolean = false,
    val isLoadingAllCourts: Boolean = false,
    val isActionInProgress: Boolean = false,
    val showEquipmentDialog: Boolean = false,
    val showAddCourtDialog: Boolean = false,
    val editingEquipment: EquipmentDto? = null,
    val equipmentNameInput: String = "",
    val equipmentTypeInput: EquipmentType = EquipmentType.RACKET,
    val equipmentIsDefaultInput: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val friendCount: Int get() = friends.size
    val isLoadingAny: Boolean
        get() = isLoadingUserMe || isLoadingFriends || isLoadingRequests || isLoadingMatches || isLoadingEquipment || isLoadingPreferredCourts
}

// The rating graph needs this many matches with a recorded rating
const val MIN_TREND_MATCHES = 3

/**
 * @return e.g. "12–7": wins, then losses, of the matches with a winner
 */
fun recordText(matchHistory: List<MatchResult>): String {
    val wins = matchHistory.count { it.outcome == MatchOutcome.WIN }
    val losses = matchHistory.count { it.outcome == MatchOutcome.LOSS }
    return "$wins–$losses"
}

/**
 * The player's rating over time, oldest first: before their first recorded match, then after each one
 * @param matchHistory most recent first, as in ProfileUiState
 * @return empty when fewer than MIN_TREND_MATCHES matches have a recorded rating
 */
fun ratingTrend(matchHistory: List<MatchResult>): List<Int> {
    val rated = matchHistory.filter { it.me.ratingBefore != null && it.me.ratingAfter != null }.reversed()
    if (rated.size < MIN_TREND_MATCHES) {
        return emptyList()
    }
    val start = rated.first().me.ratingBefore ?: return emptyList()
    return listOf(start.roundToInt()) + rated.mapNotNull { it.me.ratingAfter?.roundToInt() }
}
