package com.example.cpen321application.core.network.dto

import com.google.gson.annotations.SerializedName

data class AcceptFriendRequestDto(
    @SerializedName("userId", alternate = ["id", "senderId"])
    val userId: String
)
