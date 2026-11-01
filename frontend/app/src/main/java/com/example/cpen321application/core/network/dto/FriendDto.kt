package com.example.cpen321application.core.network.dto

import com.google.gson.annotations.SerializedName

data class FriendDto(
    @SerializedName("userId", alternate = ["id", "_id"])
    val userId: String,
    val username: String,
    val elo: Int? = null,
    val avatarUrl: String? = null
)
