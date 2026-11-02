package com.example.cpen321application.feature.leaderboard

import com.example.cpen321application.core.network.dto.LeaderboardEntryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardUiStateTest {
    private fun entry(id: String, rank: Int) =
        LeaderboardEntryDto(id = id, username = id, name = id, rating = 1500.0, rank = rank)

    private val top = listOf(entry("a", 1), entry("b", 2), entry("c", 3))

    @Test
    fun myIndex_userInTopPlayers_isTheirPosition() {
        val state = LeaderboardUiState(entries = top, me = entry("b", 2))
        assertEquals(1, state.myIndex)
    }

    @Test
    fun myIndex_userOutsideTopPlayers_isMinusOne() {
        val state = LeaderboardUiState(entries = top, me = entry("z", 120))
        assertEquals(-1, state.myIndex)
    }

    @Test
    fun myIndex_noUser_isMinusOne() {
        val state = LeaderboardUiState(entries = top, me = null)
        assertEquals(-1, state.myIndex)
    }
}
