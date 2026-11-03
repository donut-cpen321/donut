package com.example.cpen321application.feature.matchsummary

import com.example.cpen321application.core.data.model.MatchResult

// Where the signed-in user stands with another player of the match
enum class FriendStatus {
    FRIENDS,
    REQUEST_SENT,  // the signed-in user sent a request that is still pending
    REQUEST_RECEIVED,  // the other player sent one; sending a request back accepts it
    NOT_FRIENDS
}

/**
 * @property match the completed game from the signed-in user's point of view, null until it loads
 * @property friendStatuses the signed-in user's friendship with each other player, by user id
 * @property sendingRequestTo the players a friend request is being sent to right now
 * @property friendMessage the result of the last friend request, e.g. "Friend request sent"
 * @property friendErrorMessage why the last friend request failed
 */
data class MatchSummaryUiState(
    val isLoading: Boolean = false,
    val match: MatchResult? = null,
    val friendStatuses: Map<String, FriendStatus> = emptyMap(),
    val sendingRequestTo: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val friendMessage: String? = null,
    val friendErrorMessage: String? = null
)
