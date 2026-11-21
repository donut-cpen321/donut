package com.donut.app.core.network.dto

import com.google.gson.annotations.SerializedName

data class UserMeDto(
    @SerializedName("id", alternate = ["userId", "_id"])
    val id: String? = null,
    val username: String? = null,
    // The player's real name, e.g. "Alex Rivera"
    val name: String? = null,
    val email: String? = null,
    @SerializedName("avatarUrl", alternate = ["photoUrl", "photo", "picture", "url"])
    val avatarUrl: String? = null,
    // The rating, rounded. The backend sends a decimal ("rating": 1584.6), and read into an Int it came out
    // cut short (1584) while the leaderboard and game screens round it (1585). So Gson skips this field
    // (@Transient), and FriendsRepository reads and rounds the rating itself
    @Transient
    val elo: Int? = null,
)
