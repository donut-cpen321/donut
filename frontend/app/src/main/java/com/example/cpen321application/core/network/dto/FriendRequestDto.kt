package com.example.cpen321application.core.network.dto

import com.google.gson.annotations.SerializedName

data class FriendRequestDto(
    @SerializedName("userId", alternate = ["id", "_id", "senderId", "fromUserId"])
    val userId: String,
    val username: String,
    val createdAt: String? = null,
)
