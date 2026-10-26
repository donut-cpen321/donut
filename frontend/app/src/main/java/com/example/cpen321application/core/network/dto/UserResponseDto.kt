package com.example.cpen321application.core.network.dto

import com.example.cpen321application.core.auth.User

data class UserResponseDto(
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

fun UserResponseDto.toUser(): User = User(
    username = username,
    accessToken = accessToken,
    refreshToken = refreshToken
)
