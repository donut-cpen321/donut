package com.example.cpen321application.core.network.dto

import com.google.gson.annotations.SerializedName

data class MatchDto(
    @SerializedName("id", alternate = ["_id", "matchId"])
    val id: String,
    val opponentUsername: String,
    val result: String, // "WIN", "LOSS", "DRAW"
    val score: String,
    val date: String,
    val eloChange: Int? = null
)
