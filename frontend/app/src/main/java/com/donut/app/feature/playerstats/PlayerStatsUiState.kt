package com.donut.app.feature.playerstats

import com.donut.app.core.network.dto.PlayerStatsDto
import kotlin.math.roundToInt

private const val PERCENT = 100

data class PlayerStatsUiState(
    val isLoading: Boolean = false,
    val stats: PlayerStatsDto? = null,
    val errorMessage: String? = null,
) {
    /** Ratings to plot, oldest first: the rating before the first game, then the rating after each game */
    val chartRatings: List<Double>
        get() {
            val history = stats?.ratingHistory.orEmpty()
            val start = history.firstOrNull()?.ratingBefore ?: return emptyList()
            return listOf(start) + history.map { it.ratingAfter }
        }

    /** Share of rated games won, rounded to a whole percent, null before the first game */
    val winRatePercent: Int?
        get() = stats?.takeIf { it.gamesPlayed > 0 }?.let { it.wins * PERCENT.toDouble() / it.gamesPlayed }
            ?.roundToInt()

    /** How much the rating moved over every rated game, rounded, null before the first game */
    val totalRatingChange: Int?
        get() = chartRatings.takeIf { it.isNotEmpty() }?.let { (it.last() - it.first()).roundToInt() }
}
