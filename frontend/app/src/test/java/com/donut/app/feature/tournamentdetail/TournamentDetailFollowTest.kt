package com.donut.app.feature.tournamentdetail

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.model.TournamentCreator
import com.donut.app.core.data.model.TournamentPlayer
import com.donut.app.core.data.model.TournamentStatus
import com.donut.app.testing.UnusedTournamentsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant

// Only tournamentUpdates is used: the screen's live updates
private class FakeTournamentsRepository(val updates: Flow<Tournament>) : UnusedTournamentsRepository() {
    override fun tournamentUpdates(tournamentId: String): Flow<Tournament> = updates
}

class TournamentDetailFollowTest {
    private val alice = TournamentPlayer("u1", "alice", "Alice A", 1500.0, null)
    private val bob = TournamentPlayer("u2", "bob", "Bob B", 1500.0, null)

    private fun tournament(players: List<TournamentPlayer>) = Tournament(
        id = "t1",
        name = "Sunday Smash",
        format = Format.SINGLES,
        status = TournamentStatus.REGISTRATION,
        startsAt = Instant.now().plus(Duration.ofDays(1)),
        court = Court(1, "Stanley Park", 49.3, -123.14),
        creator = TournamentCreator("u1", "alice", "Alice A", 1500.0),
        capacity = 8,
        players = players
    )

    // Viewed by bob
    private fun viewModel(updates: Flow<Tournament>) =
        TournamentDetailViewModel("t1", FakeTournamentsRepository(updates), currentUsername = { "bob" })

    // Input: the screen opens (loading), then the backend pushes the tournament, then again once bob joined
    // Expected output: the latest tournament and its actions: bob is in, so he can leave, not join
    @Test(timeout = 10_000)
    fun pushedTournamentIsShown() = runTest {
        val viewModel = viewModel(flowOf(tournament(listOf(alice)), tournament(listOf(alice, bob))))
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.followUpdates()

        val state = viewModel.uiState.value
        assertEquals(listOf("u1", "u2"), state.tournament?.players?.map { it.id })
        assertTrue(state.actions?.canLeave == true)
        assertFalse(state.actions?.canJoin == true)
        assertFalse(state.isLoading)
    }

    // Input: the first connection drops, the second pushes the tournament
    // Expected behavior: it reconnects after the delay (virtual time here)
    // Expected output: two connections, and the tournament shown
    @Test(timeout = 10_000)
    fun reconnectsAfterADroppedConnection() = runTest {
        var connections = 0
        val viewModel = viewModel(
            flow {
                if (connections++ == 0) throw IOException("connection dropped")
                emit(tournament(listOf(alice)))
            }
        )

        viewModel.followUpdates()

        assertEquals(2, connections)
        assertEquals("t1", viewModel.uiState.value.tournament?.id)
    }

    // Input: the backend refuses the stream with 404
    // Expected output: it stops loading and shows the error, without crashing
    @Test(timeout = 10_000)
    fun refusedStreamShowsTheError() = runTest {
        val viewModel = viewModel(flow { throw HttpException(Response.error<Unit>(404, "".toResponseBody())) })

        viewModel.followUpdates()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Request failed (HTTP 404)", viewModel.uiState.value.errorMessage)
    }
}
