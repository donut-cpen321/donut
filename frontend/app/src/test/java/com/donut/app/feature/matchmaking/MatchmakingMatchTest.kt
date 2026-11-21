package com.donut.app.feature.matchmaking

import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

// Showing a match, and accepting or cancelling it
class MatchmakingMatchTest : MatchmakingViewModelTestBase() {
    // Input: the screen opens while the player has a match they haven't answered (e.g. after an app restart)
    // Expected output: the match pop-up shows it again
    @Test
    fun loadMatchShowsThePendingMatch() = runBlocking {
        val booked = match(status = GameStatus.BOOKED).copy(id = "game-0")
        games.myGamesResult = Result.success(listOf(booked, match()))
        val viewModel = newViewModel()

        viewModel.loadMatch()

        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: Accept tapped; the backend accepts and bob hasn't answered yet
    // Expected output: the acceptance is sent and the pop-up shows the refreshed game
    @Test
    fun acceptRefreshesTheMatch() = runBlocking {
        val viewModel = matchedViewModel()
        val refreshed = match(aliceAcceptedAt = Instant.parse("2026-11-02T12:05:00Z"))
        games.gameResult = Result.success(refreshed)

        viewModel.onAccept()

        assertEquals(listOf("game-1"), games.accepted)
        assertEquals(refreshed, viewModel.uiState.value.match)
        assertFalse(viewModel.uiState.value.isAnswering)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: Accept tapped; the backend is unreachable and the game is still open
    // Expected output: the pop-up stays with an error
    @Test
    fun acceptFailureKeepsTheMatchAndShowsAnError() = runBlocking {
        val viewModel = matchedViewModel()
        games.acceptResult = Result.failure(IOException("offline"))
        games.gameResult = Result.success(match())

        viewModel.onAccept()

        assertEquals(match(), viewModel.uiState.value.match)
        assertEquals(ACCEPT_FAILED_MESSAGE, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isAnswering)
    }

    // Input: Accept tapped inside the 30-minute cutoff; the backend refuses with 409 and the deadline job
    // hasn't cancelled the game yet
    // Expected output: the pop-up stays and says it's too late, not that the connection failed
    @Test
    fun acceptInsideTheCutoffSaysItIsTooLate() = runBlocking {
        val viewModel = matchedViewModel()
        games.acceptResult = Result.failure(HttpException(Response.error<Unit>(409, "".toResponseBody())))
        games.gameResult = Result.success(match())

        viewModel.onAccept()

        assertEquals(match(), viewModel.uiState.value.match)
        assertEquals(ACCEPT_TOO_LATE_MESSAGE, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isAnswering)
    }

    // Input: Accept tapped, but an earlier accept from this player already went through (e.g. a retried request),
    // so the backend refuses with 409 "Match already accepted"
    // Expected output: no error, the pop-up shows the refreshed game with the player accepted
    @Test
    fun acceptAlreadyAcceptedIsNotAnError() = runBlocking {
        val viewModel = matchedViewModel()
        val body = """{"error":"Match already accepted"}""".toResponseBody()
        games.acceptResult = Result.failure(HttpException(Response.error<Unit>(409, body)))
        val refreshed = match(aliceAcceptedAt = Instant.parse("2026-11-02T12:05:00Z"))
        games.gameResult = Result.success(refreshed)

        viewModel.onAccept()

        assertEquals(refreshed, viewModel.uiState.value.match)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isAnswering)
    }

    // Input: Accept tapped after the other player or the deadline cancelled the game
    // Expected output: "Match fell through" instead of an error
    @Test
    fun acceptOnACancelledGameShowsFellThrough() = runBlocking {
        val viewModel = matchedViewModel()
        games.acceptResult = Result.failure(IOException("409"))
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))

        viewModel.onAccept()

        assertNull(viewModel.uiState.value.match)
        assertTrue(viewModel.uiState.value.matchFellThrough)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: Cancel tapped; the backend cancels the game
    // Expected output: the pop-up closes, nothing else shows
    @Test
    fun cancelClearsTheMatch() = runBlocking {
        val viewModel = matchedViewModel()

        viewModel.onCancelMatch()

        assertEquals(listOf("game-1"), games.cancelled)
        assertNull(viewModel.uiState.value.match)
        assertFalse(viewModel.uiState.value.matchFellThrough)
    }

    // Input: Cancel tapped; the backend is unreachable
    // Expected output: the pop-up stays with an error
    @Test
    fun cancelFailureShowsAnError() = runBlocking {
        val viewModel = matchedViewModel()
        games.cancelResult = Result.failure(IOException("offline"))

        viewModel.onCancelMatch()

        assertEquals(match(), viewModel.uiState.value.match)
        assertEquals(CANCEL_FAILED_MESSAGE, viewModel.uiState.value.errorMessage)
    }

    // Input: the 10-second check while the pop-up is open; the other player cancelled
    // Expected output: the pop-up turns into "Match fell through"
    @Test
    fun checkMatchShowsFellThroughWhenCancelled() = runBlocking {
        val viewModel = matchedViewModel()
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))

        viewModel.checkMatch()

        assertNull(viewModel.uiState.value.match)
        assertTrue(viewModel.uiState.value.matchFellThrough)
    }

    // Input: the 10-second check fails because the backend is unreachable
    // Expected output: the pop-up keeps the match it had
    @Test
    fun checkMatchFailureKeepsTheMatch() = runBlocking {
        val viewModel = matchedViewModel()
        games.gameResult = Result.failure(IOException("offline"))

        viewModel.checkMatch()

        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: "Match fell through" was shown, then OK tapped
    // Expected output: the dialog is gone
    @Test
    fun matchFellThroughSeenClosesTheDialog() = runBlocking {
        val viewModel = matchedViewModel()
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))
        viewModel.checkMatch()

        viewModel.onMatchFellThroughSeen()

        assertFalse(viewModel.uiState.value.matchFellThrough)
    }

    // Input: Find a match tapped while a match is already coming up
    // Expected output: not searching, the error says why
    @Test
    fun findMatchWithAnActiveMatchExplainsWhy() = runBlocking {
        repository.joinResult = Result.failure(ActiveMatchException())
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(ACTIVE_MATCH_MESSAGE, viewModel.uiState.value.errorMessage)
    }

    // Input: the player has a scheduled game coming up before their matched game
    // Expected output: the pop-up shows the matched game, not the scheduled one
    @Test
    fun loadMatchSkipsScheduledGames() = runBlocking {
        val scheduled = match().copy(id = "game-0", source = GameSource.SCHEDULED)
        games.myGamesResult = Result.success(listOf(scheduled, match()))
        val viewModel = newViewModel()

        viewModel.loadMatch()

        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: a failed Find a match left an error, then the screen finds a pending match
    // Expected output: the pop-up shows without the old error
    @Test
    fun loadMatchClearsAStaleError() = runBlocking {
        repository.joinResult = Result.failure(IOException("offline"))
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        viewModel.onFindMatch()
        games.myGamesResult = Result.success(listOf(match()))

        viewModel.loadMatch()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: an accept failed (error shown), then the next check finds the game cancelled
    // Expected output: "Match fell through" without the old accept error
    @Test
    fun checkMatchFellThroughClearsTheError() = runBlocking {
        val viewModel = matchedViewModel()
        games.acceptResult = Result.failure(IOException("offline"))
        games.gameResult = Result.success(match())
        viewModel.onAccept()
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))

        viewModel.checkMatch()

        assertTrue(viewModel.uiState.value.matchFellThrough)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: Cancel tapped after the other player or the deadline already cancelled the game
    // Expected output: "Match fell through" instead of a connection error
    @Test
    fun cancelOnACancelledGameShowsFellThrough() = runBlocking {
        val viewModel = matchedViewModel()
        games.cancelResult = Result.failure(IOException("409"))
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))

        viewModel.onCancelMatch()

        assertNull(viewModel.uiState.value.match)
        assertTrue(viewModel.uiState.value.matchFellThrough)
        assertNull(viewModel.uiState.value.errorMessage)
    }

}
