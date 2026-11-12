package com.example.cpen321application.feature.matchmaking

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.data.repository.GamesRepository
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

/**
 * Stands in for the backend: returns whatever each test sets and records what was sent
 */
private class FakeMatchmakingRepository : MatchmakingRepository {
    var joinResult: Result<Unit> = Result.success(Unit)
    var leaveResult: Result<Unit> = Result.success(Unit)
    var queuedResult: Result<MatchFormat?> = Result.success(null)
    val joined = mutableListOf<Pair<MatchFormat, MatchLocation>>()
    var leaveCalls = 0

    override suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit> {
        joined.add(format to location)
        return joinResult
    }

    override suspend fun leaveQueue(): Result<Unit> {
        leaveCalls++
        return leaveResult
    }

    override suspend fun queuedFormat(): Result<MatchFormat?> = queuedResult
}

/**
 * Stands in for the games backend: only the calls the Matchmaking screen makes return anything
 */
private class FakeGamesRepository : GamesRepository {
    var myGamesResult: Result<List<Game>> = Result.success(emptyList())
    var gameResult: Result<Game> = Result.failure(IOException("not set"))
    var acceptResult: Result<Unit> = Result.success(Unit)
    var cancelResult: Result<Unit> = Result.success(Unit)
    val accepted = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override suspend fun getMyGames(): Result<List<Game>> = myGamesResult

    override suspend fun getGame(gameId: String): Result<Game> = gameResult

    override suspend fun acceptMatch(gameId: String): Result<Unit> {
        accepted.add(gameId)
        return acceptResult
    }

    override suspend fun cancelGame(gameId: String): Result<Unit> {
        cancelled.add(gameId)
        return cancelResult
    }

    override suspend fun getFeed(): Result<List<Game>> = error("unused")
    override suspend fun createGame(format: Format, startsAt: Instant, courtId: Int): Result<String> = error("unused")
    override suspend fun joinGame(gameId: String, team: Int): Result<Unit> = error("unused")
    override suspend fun leaveGame(gameId: String): Result<Unit> = error("unused")
    override suspend fun inviteFriend(gameId: String, userId: String): Result<Boolean> = error("unused")
    override suspend fun changeGameTime(gameId: String, startsAt: Instant): Result<Unit> = error("unused")
    override suspend fun changeGameLocation(gameId: String, courtId: Int): Result<Unit> = error("unused")
    override suspend fun reportScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit> = error("unused")
}

class MatchmakingViewModelTest {
    private val ubc = MatchLocation("UBC Tennis Centre", 49.2606, -123.2460)
    private val repository = FakeMatchmakingRepository()
    private val games = FakeGamesRepository()

    private fun newViewModel() = MatchmakingViewModel(repository, games)

    // A singles match between alice (the signed-in user) and bob, still waiting for answers
    private fun match(
        status: GameStatus = GameStatus.OPEN,
        aliceAcceptedAt: Instant? = null
    ) = Game(
        id = "game-1",
        format = Format.SINGLES,
        status = status,
        source = GameSource.MATCHED,
        startsAt = Instant.parse("2026-11-02T13:00:00Z"),
        court = Court(1, "UBC Tennis Centre", 49.26, -123.25),
        players = listOf(
            Player("u1", "alice", "Alice A", 1500.0, 1, acceptedAt = aliceAcceptedAt),
            Player("u2", "bob", "Bob B", 1540.0, 2)
        ),
        rounds = emptyList()
    )

    // A view model showing the pending match
    private fun matchedViewModel(): MatchmakingViewModel {
        games.myGamesResult = Result.success(listOf(match()))
        val viewModel = newViewModel()
        runBlocking { viewModel.loadMatch() }
        return viewModel
    }

    // A view model already waiting in the queue near UBC
    private fun queuedViewModel(): MatchmakingViewModel {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        runBlocking { viewModel.onFindMatch() }
        return viewModel
    }

    // Input: nothing picked yet
    // Expected output: no option, no location, Find a match disabled
    @Test
    fun startsWithNoLocation() {
        val state = newViewModel().uiState.value

        assertNull(state.selectedOption)
        assertNull(state.location)
        assertFalse(state.canFindMatch)
    }

    // Input: a location was found, then the user picks the other option
    // Expected output: the new option is selected and the old location and error are cleared
    @Test
    fun pickingAnOptionClearsThePreviousLocation() {
        val viewModel = newViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
        viewModel.onLocationFound(ubc)

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        val state = viewModel.uiState.value
        assertEquals(LocationOption.ADDRESS, state.selectedOption)
        assertNull(state.location)
        assertNull(state.errorMessage)
    }

    // Input: the current location is loading, then the user switches to Address
    // Expected output: not loading, since the screen ignores the abandoned lookup when it finishes
    @Test
    fun pickingAnOptionStopsThePreviousLoading() {
        val viewModel = newViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
        viewModel.onLocationLoading()

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        assertFalse(viewModel.uiState.value.isLoading)
    }

    // Input: the user denies the location permission
    // Expected output: an error pointing them to the address option, no location
    @Test
    fun deniedPermissionShowsError() {
        val viewModel = newViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)

        viewModel.onPermissionDenied()

        val state = viewModel.uiState.value
        assertEquals(PERMISSION_DENIED_MESSAGE, state.errorMessage)
        assertNull(state.location)
        assertFalse(state.canFindMatch)
    }

    // Input: the location is being fetched
    // Expected output: loading, Find a match disabled
    @Test
    fun loadingDisablesFindMatch() {
        val viewModel = newViewModel()

        viewModel.onLocationLoading()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.canFindMatch)
    }

    // Input: the location arrives after loading (current location or a picked address)
    // Expected output: location saved, loading done, Find a match enabled
    @Test
    fun foundLocationEnablesFindMatch() {
        val viewModel = newViewModel()
        viewModel.onLocationLoading()

        viewModel.onLocationFound(ubc)

        val state = viewModel.uiState.value
        assertEquals(ubc, state.location)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(state.canFindMatch)
    }

    // Input: the location could not be read (GPS off, Places error)
    // Expected output: the error is shown, loading done, no location
    @Test
    fun failedLocationShowsError() {
        val viewModel = newViewModel()
        viewModel.onLocationLoading()

        viewModel.onLocationFailed("Could not get your location")

        val state = viewModel.uiState.value
        assertEquals("Could not get your location", state.errorMessage)
        assertFalse(state.isLoading)
        assertNull(state.location)
    }

    // Input: Find a match tapped with no location
    // Expected output: nothing happens
    @Test
    fun findMatchWithoutLocationDoesNothing() = runBlocking {
        val viewModel = newViewModel()

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(repository.joined.isEmpty())
    }

    // Input: Doubles picked, location found, Find a match tapped; the backend accepts
    // Expected behavior: joins the queue with that format and location
    // Expected output: searching
    @Test
    fun findMatchJoinsTheQueue() = runBlocking {
        val viewModel = newViewModel()
        viewModel.onFormatSelected(MatchFormat.DOUBLES)
        viewModel.onLocationFound(ubc)

        viewModel.onFindMatch()

        assertEquals(listOf(MatchFormat.DOUBLES to ubc), repository.joined)
        assertTrue(viewModel.uiState.value.isSearching)
        assertFalse(viewModel.uiState.value.isUpdatingQueue)
    }

    // Input: Find a match tapped; the backend is unreachable
    // Expected output: not searching, an error asks to try again
    @Test
    fun findMatchFailureShowsError() = runBlocking {
        repository.joinResult = Result.failure(IOException("offline"))
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)

        viewModel.onFindMatch()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertFalse(state.isUpdatingQueue)
        assertEquals(JOIN_FAILED_MESSAGE, state.errorMessage)
    }

    // Input: Find a match tapped while a search is already running
    // Expected output: Find a match is disabled and a second tap does not join again
    @Test
    fun searchingDisablesFindMatch() = runBlocking {
        val viewModel = queuedViewModel()

        viewModel.onFindMatch()

        assertEquals(1, repository.joined.size)
        val state = viewModel.uiState.value
        assertTrue(state.isSearching)
        assertFalse(state.canFindMatch)
    }

    // Input: a location was found, a new lookup is loading, then Find a match is tapped
    // Expected output: no search starts while the location is loading
    @Test
    fun findMatchWhileLoadingDoesNothing() = runBlocking {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        viewModel.onLocationLoading()

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
    }

    // Input: nothing picked yet
    // Expected output: the format is Either, so the player gets the most matches
    @Test
    fun formatStartsAsEither() {
        assertEquals(MatchFormat.EITHER, newViewModel().uiState.value.format)
    }

    // Input: a location was found, then the player picks Doubles
    // Expected output: the format is Doubles and the location is kept
    @Test
    fun pickingAFormatKeepsTheLocation() {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)

        viewModel.onFormatSelected(MatchFormat.DOUBLES)

        val state = viewModel.uiState.value
        assertEquals(MatchFormat.DOUBLES, state.format)
        assertEquals(ubc, state.location)
    }

    // Input: Doubles was picked, then the player switches location option
    // Expected output: the format stays Doubles
    @Test
    fun pickingALocationOptionKeepsTheFormat() {
        val viewModel = newViewModel()
        viewModel.onFormatSelected(MatchFormat.DOUBLES)

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        assertEquals(MatchFormat.DOUBLES, viewModel.uiState.value.format)
    }

    // Input: waiting in the queue, then the player changes the format
    // Expected output: nothing changes, they have to leave the queue first
    @Test
    fun pickingAFormatWhileQueuedDoesNothing() {
        val viewModel = queuedViewModel()

        viewModel.onFormatSelected(MatchFormat.SINGLES)

        val state = viewModel.uiState.value
        assertEquals(MatchFormat.EITHER, state.format)
        assertTrue(state.isSearching)
    }

    // Input: waiting in the queue, then the player picks the other location option
    // Expected output: nothing changes, they have to leave the queue first
    @Test
    fun pickingALocationOptionWhileQueuedDoesNothing() {
        val viewModel = queuedViewModel()

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        val state = viewModel.uiState.value
        assertEquals(ubc, state.location)
        assertTrue(state.isSearching)
    }

    // Input: waiting in the queue, Leave queue tapped; the backend accepts
    // Expected output: not searching, Find a match enabled again
    @Test
    fun leaveQueueStopsSearching() = runBlocking {
        val viewModel = queuedViewModel()

        viewModel.onLeaveQueue()

        val state = viewModel.uiState.value
        assertEquals(1, repository.leaveCalls)
        assertFalse(state.isSearching)
        assertTrue(state.canFindMatch)
    }

    // Input: waiting in the queue, Leave queue tapped; the backend is unreachable
    // Expected output: still searching, an error asks to try again
    @Test
    fun leaveQueueFailureKeepsSearching() = runBlocking {
        repository.leaveResult = Result.failure(IOException("offline"))
        val viewModel = queuedViewModel()

        viewModel.onLeaveQueue()

        val state = viewModel.uiState.value
        assertTrue(state.isSearching)
        assertEquals(LEAVE_FAILED_MESSAGE, state.errorMessage)
    }

    // Input: Leave queue tapped while not in the queue
    // Expected output: the backend is not called
    @Test
    fun leaveQueueWhenNotQueuedDoesNothing() = runBlocking {
        newViewModel().onLeaveQueue()

        assertEquals(0, repository.leaveCalls)
    }

    // Input: the 10-second check while the player is still waiting
    // Expected output: still searching, no match
    @Test
    fun checkQueueWhileWaitingKeepsSearching() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(MatchFormat.EITHER)

        viewModel.checkQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
    }

    // Input: the check after matchmaking took the player out of the queue with a game
    // Expected output: not searching, the match pop-up shows that game
    @Test
    fun checkQueueShowsTheMatch() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(listOf(match()))

        viewModel.checkQueue()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: out of the queue, but the games lookup fails
    // Expected output: still searching, so the next check looks again
    @Test
    fun checkQueueKeepsSearchingWhenTheLookupFails() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.failure(IOException("offline"))

        viewModel.checkQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
    }

    // Input: out of the queue, but the match was already cancelled by another player
    // Expected output: not searching, "Match fell through" shows
    @Test
    fun checkQueueWithoutAPendingMatchShowsFellThrough() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(emptyList())

        viewModel.checkQueue()

        assertFalse(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
        assertTrue(viewModel.uiState.value.matchFellThrough)
    }

    // Input: the check fails because the backend is unreachable
    // Expected output: still searching, a network problem is not a match
    @Test
    fun checkQueueFailureKeepsSearching() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.failure(IOException("offline"))

        viewModel.checkQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
    }

    // Input: the check answers "not in the queue" after the player already left
    // Expected output: no match found dialog, leaving is not a match
    @Test
    fun checkQueueAfterLeavingDoesNothing() = runBlocking {
        val viewModel = queuedViewModel()
        viewModel.onLeaveQueue()
        repository.queuedResult = Result.success(null)

        viewModel.checkQueue()

        assertNull(viewModel.uiState.value.match)
        assertFalse(viewModel.uiState.value.matchFellThrough)
    }

    // Input: the screen opens while the player is already waiting for doubles (e.g. after an app restart)
    // Expected output: searching again, with the format they are waiting for
    @Test
    fun restoreQueueResumesSearching() = runBlocking {
        repository.queuedResult = Result.success(MatchFormat.DOUBLES)
        val viewModel = newViewModel()

        viewModel.restoreQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(MatchFormat.DOUBLES, viewModel.uiState.value.format)
    }

    // Input: the screen opens and the player is not in the queue
    // Expected output: not searching
    @Test
    fun restoreQueueWhenNotQueuedStaysIdle() = runBlocking {
        val viewModel = newViewModel()

        viewModel.restoreQueue()

        assertFalse(viewModel.uiState.value.isSearching)
    }

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
