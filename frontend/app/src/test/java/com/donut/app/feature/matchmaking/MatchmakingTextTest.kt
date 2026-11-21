package com.donut.app.feature.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchmakingTextTest {

    @Test
    fun searchingTextNamesThePlace() {
        assertEquals("Finding a match", searchingText(null))
        assertEquals("Finding a match near you", searchingText(MatchLocation(CURRENT_LOCATION_LABEL, 49.0, -123.0)))
        assertEquals("Finding a match near Jericho Beach", searchingText(MatchLocation("Jericho Beach", 49.0, -123.0)))
    }

    @Test
    fun ratingRangeIsWiderForDoubles() {
        assertEquals("±200 rating", ratingRangeText(MatchFormat.SINGLES))
        assertEquals("±400 rating", ratingRangeText(MatchFormat.DOUBLES))
        assertEquals("±200–400 rating", ratingRangeText(MatchFormat.EITHER))
    }

    @Test
    fun criteriaListDistanceRatingAndStart() {
        assertEquals(listOf("Within 10 km", "±400 rating", "Game in ~1 h"), criteriaChips(MatchFormat.DOUBLES))
        assertEquals("10 km · ±200 rating · starts ~1 h after match", criteriaText(MatchFormat.SINGLES))
    }

    @Test
    fun elapsedTextIsMinutesAndSeconds() {
        assertEquals("0:00", elapsedText(0))
        assertEquals("0:42", elapsedText(42))
        assertEquals("12:05", elapsedText(725))
    }

    @Test
    fun distanceBetweenTwoVancouverParks() {
        // Kitsilano Beach Park to Jericho Beach Park, about 3.1 km apart
        val km = distanceKm(49.2734, -123.1530, 49.2722, -123.1960)

        assertEquals(3.1, km, 0.1)
    }

    @Test
    fun distanceIsZeroToItself() {
        assertEquals(0.0, distanceKm(49.27, -123.15, 49.27, -123.15), 1e-9)
    }

    @Test
    fun distanceTextUsesMetresUnderOneKm() {
        assertEquals("2.1 km", distanceText(2.14))
        assertEquals("850 m", distanceText(0.84))
        assertEquals("50 m", distanceText(0.001))
    }
}
