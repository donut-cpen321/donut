package com.donut.app.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * A pending friend request, as GET /friends/requests and the friends page stream send it: the other user
 * (who sent it, or who it was sent to) and when it was sent
 */
data class FriendRequestDto(
    @SerializedName("userId", alternate = ["id", "_id", "senderId", "fromUserId"])
    val userId: String,
    val username: String,
    // When the request was sent, e.g. "2026-11-19T20:39:51.000Z". The backend calls it "requestedAt"
    @SerializedName("requestedAt", alternate = ["createdAt"])
    val requestedAt: String? = null,
    // Their real name, e.g. "Chance Test"; the username is their sign-up email
    val name: String? = null,
    // Their rating, a decimal like 1520.37
    @SerializedName("rating", alternate = ["elo"])
    val rating: Double? = null,
    val avatarUrl: String? = null,
) {
    // The name to show, or the username if they have no name
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username
}
