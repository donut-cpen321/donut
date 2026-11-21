package com.donut.app.core.network.dto

import com.google.gson.annotations.SerializedName

data class AcceptFriendRequestDto(
    @SerializedName("userId", alternate = ["id", "senderId"])
    val userId: String,
)
