package com.example.cpen321application.feature.leaderboard

import com.example.cpen321application.core.network.dto.LeaderboardEntryDto

data class LeaderboardUiState(
    val isLoading: Boolean = false,
    val entries: List<LeaderboardEntryDto> = emptyList(),
    val me: LeaderboardEntryDto? = null,
    val errorMessage: String? = null,
) {
    /** Index of the signed-in user in [entries], -1 if they are outside the top players */
    val myIndex: Int
        get() = me?.let { mine -> entries.indexOfFirst { it.id == mine.id } } ?: -1
}
