package com.donut.app.core.network.dto

/**
 * Response of GET /stats
 * @property stats the player's statistics
 */
data class PlayerStatsResponseDto(
    val stats: PlayerStatsDto? = null,
)

/**
 * A player's statistics for one sport
 * @property user the player with their current rating and leaderboard rank
 * @property gamesPlayed rated games (matches) played
 * @property pointsWon in tennis, games won summed over every set
 * @property ratingHistory the rating before and after each rated game, oldest first
 * @property vsOpponentRating results by the opponents' rating before the game, lowest band first
 */
data class PlayerStatsDto(
    val user: LeaderboardEntryDto? = null,
    val sport: String = "TENNIS",
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val setsPlayed: Int = 0,
    val setsWon: Int = 0,
    val setsLost: Int = 0,
    val pointsWon: Int = 0,
    val pointsLost: Int = 0,
    val ratingHistory: List<RatingPointDto> = emptyList(),
    val vsOpponentRating: List<OpponentRatingBandDto> = emptyList(),
)

/**
 * One rated game on the rating chart
 * @property playedAt when the game started, e.g. 2026-11-10T17:00:00.000Z
 */
data class RatingPointDto(
    val gameId: String,
    val playedAt: String,
    val ratingBefore: Double,
    val ratingAfter: Double,
)

/**
 * Results against opponents rated at least [minRating] and below [maxRating] before the game
 * @property averageMargin the player's points minus the opponents' points, per game
 */
data class OpponentRatingBandDto(
    val minRating: Int,
    val maxRating: Int,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val averageMargin: Double,
)
