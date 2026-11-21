package com.donut.app.core.network.dto

import com.google.gson.annotations.SerializedName

data class FriendDto(
    @SerializedName("userId", alternate = ["id", "_id"])
    val userId: String,
    val username: String,
    // The backend sends "rating", a decimal like 1520.37 (it used to be read as "elo", which never matched)
    @SerializedName("rating", alternate = ["elo"])
    val rating: Double? = null,
    @SerializedName("photoUrl", alternate = ["avatarUrl"])
    val avatarUrl: String? = null,
    // The game the friend is playing right now, null if they aren't playing
    val playing: FriendGameDto? = null,
    // Their real name, e.g. "Chance Test"; the username is their sign-up email
    val name: String? = null,
) {
    // The name to show, or the username if they have no name
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username
}

// A friend's game in progress, from GET /friends
data class FriendGameDto(
    val gameId: String,
    val format: String,
    val startedAt: String,
    val courtName: String,
)
