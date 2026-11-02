package com.example.cpen321application.core.network.dto

/**
 * Response of GET /leaderboard
 * @property leaderboard the top players in rank order
 * @property me the caller's own entry, null if the user does not exist
 */
data class LeaderboardResponseDto(
    val leaderboard: List<LeaderboardEntryDto> = emptyList(),
    val me: LeaderboardEntryDto? = null,
)

/**
 * One player on the leaderboard
 * @property rank 1-based position on the leaderboard
 * @property rating the full decimal Elo rating
 * @property avatarUrl profile picture, not sent by the backend yet
 */
data class LeaderboardEntryDto(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double,
    val rank: Int,
    val avatarUrl: String? = null,
)
