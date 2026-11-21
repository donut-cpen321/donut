package com.donut.app.core.network.dto

// Body of POST /friends/requests: the user to send the request to
data class SendFriendRequestDto(
    val userId: String,
)
