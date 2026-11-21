package com.donut.app.feature.playerstats

import com.donut.app.core.network.dto.PlayerStatsDto
import com.donut.app.core.network.dto.RatingPointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerStatsUiStateTest {
    private fun point(gameId: String, before: Double, after: Double) =
        RatingPointDto(
            gameId = gameId,
            playedAt = "2026-11-10T17:00:00.000Z",
            ratingBefore = before,
            ratingAfter = after,
        )

    private val threeGames = PlayerStatsDto(
        gamesPlayed = 3,
        wins = 2,
        losses = 1,
        ratingHistory = listOf(point("g1", 1500.0, 1573.2), point("g2", 1573.2, 1540.4), point("g3", 1540.4, 1561.9)),
    )

    // Input: three rated games
    // Expected output: the starting rating, then the rating after each game
    @Test
    fun chartRatings_startsWithTheFirstRatingBefore() {
        val state = PlayerStatsUiState(stats = threeGames)
        assertEquals(listOf(1500.0, 1573.2, 1540.4, 1561.9), state.chartRatings)
    }

    // Input: no stats loaded, and stats without rated games
    // Expected output: nothing to plot, no win rate and no rating change
    @Test
    fun noRatedGames_hasNoChartWinRateOrChange() {
        for (state in listOf(PlayerStatsUiState(), PlayerStatsUiState(stats = PlayerStatsDto()))) {
            assertEquals(emptyList<Double>(), state.chartRatings)
            assertNull(state.winRatePercent)
            assertNull(state.totalRatingChange)
        }
    }

    // Input: 2 wins in 3 games, rating 1500 to 1561.9
    // Expected output: a 67% win rate and a +62 rating change
    @Test
    fun winRateAndChange_areRounded() {
        val state = PlayerStatsUiState(stats = threeGames)
        assertEquals(67, state.winRatePercent)
        assertEquals(62, state.totalRatingChange)
    }

    // Input: positive, negative, zero and almost-zero average margins
    // Expected output: one decimal with a sign; anything that rounds to 0.0 shows ±0.0
    @Test
    fun marginText_showsTheSign() {
        assertEquals("+2.5", marginText(2.5))
        assertEquals("−1.0", marginText(-1.0))
        assertEquals("±0.0", marginText(0.0))
        assertEquals("±0.0", marginText(-0.04))
    }

    // Input: a rising history and a flat one
    // Expected output: the rising history spans its own range; the flat one gets a 1-point margin each side
    @Test
    fun chartBounds_paddsAFlatHistory() {
        assertEquals(1500.0 to 1573.2, chartBounds(listOf(1500.0, 1573.2, 1540.4)))
        assertEquals(1499.0 to 1501.0, chartBounds(listOf(1500.0, 1500.0)))
    }

    // Input: the bottom, top and middle of a 1500-1600 axis
    // Expected output: 0, 1 and 0.5
    @Test
    fun chartFraction_placesRatingsOnTheAxis() {
        val bounds = 1500.0 to 1600.0
        assertEquals(0f, chartFraction(1500.0, bounds), 1e-6f)
        assertEquals(1f, chartFraction(1600.0, bounds), 1e-6f)
        assertEquals(0.5f, chartFraction(1550.0, bounds), 1e-6f)
    }
}
