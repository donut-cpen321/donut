package com.donut.app.feature.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueMapTest {
    private val ubc = MapPoint(49.261, -123.246)
    private val kits = MapPoint(49.268, -123.168)

    // Input: two players rounded to the same point and one elsewhere
    // Expected output: three keys, the two at the same point told apart
    @Test
    fun dotKeysTellApartPlayersAtTheSamePoint() {
        assertEquals(
            listOf(DotKey(ubc, 0), DotKey(kits, 0), DotKey(ubc, 1)),
            dotKeys(listOf(ubc, kits, ubc))
        )
    }

    // Input: a player was drawn and left, another just joined
    // Expected output: the one who left is still drawn (to fade out), the new one is added after
    @Test
    fun dotsToDrawKeepsLeaversAndAddsNewcomers() {
        val left = DotKey(ubc, 0)
        val joined = DotKey(kits, 0)

        assertEquals(listOf(left, joined), dotsToDraw(drawn = listOf(left), latest = listOf(joined)))
    }

    // Input: the same players as last time
    // Expected output: no duplicates
    @Test
    fun dotsToDrawDoesNotDuplicate() {
        val dot = DotKey(ubc, 0)

        assertEquals(listOf(dot), dotsToDraw(drawn = listOf(dot), latest = listOf(dot)))
    }

    // Input: before the first load, then 0, 1 and 3 other players
    // Expected output: the line under the map for each
    @Test
    fun nearbyTextCountsThePlayers() {
        assertEquals("Looking for players nearby…", nearbyText(null))
        assertEquals("No one else is searching nearby yet", nearbyText(NearbyPlayers(ubc, emptyList())))
        assertEquals("1 other player is searching nearby", nearbyText(NearbyPlayers(ubc, listOf(kits))))
        assertEquals("3 other players are searching nearby", nearbyText(NearbyPlayers(ubc, listOf(kits, kits, ubc))))
    }
}
