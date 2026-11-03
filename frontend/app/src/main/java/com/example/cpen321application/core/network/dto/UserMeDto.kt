package com.example.cpen321application.core.network.dto

import com.google.gson.annotations.SerializedName

data class UserMeDto(
    @SerializedName("id", alternate = ["userId", "_id"])
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    @SerializedName("elo", alternate = ["rating", "eloRating"])
    val elo: Int? = null,
)
