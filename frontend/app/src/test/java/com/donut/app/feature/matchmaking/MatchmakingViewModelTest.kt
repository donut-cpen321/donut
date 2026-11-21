package com.donut.app.feature.matchmaking

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.data.repository.GameListUpdate
import com.donut.app.core.data.repository.GameListUpdates
import com.donut.app.testing.UnusedGamesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    var queueStream: Flow<QueueUpdate> = emptyFlow()
    val joined = mutableListOf<Pair<MatchFormat, MatchLocation>>()
    // Runs while the join request is on its way, e.g. a match push arriving before its answer
    var duringJoin: suspend () -> Unit = {}
    var leaveCalls = 0

    override suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit> {
        joined.add(format to location)
        duringJoin()
        return joinResult
    }

    override suspend fun leaveQueue(): Result<Unit> {
        leaveCalls++
        return leaveResult
    }

    override suspend fun queuedFormat(): Result<MatchFormat?> = queuedResult

    override fun queueUpdates(): Flow<QueueUpdate> = queueStream
}

/**
 * Stands in for the games backend: only the calls the Matchmaking screen makes return anything
 */
private class FakeGamesRepository : UnusedGamesRepository() {
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
}

/**
 * Stands in for the Games tab's live lists: replays whatever each test sets, then ends
 */
private class FakeGameListUpdates : GameListUpdates {
    var stream: Flow<GameListUpdate> = emptyFlow()

    override fun updates(): Flow<GameListUpdate> = stream
}

class MatchmakingViewModelTest {
    private val ubc = MatchLocation("UBC Tennis Centre", 49.2606, -123.2460)
    private val repository = FakeMatchmakingRepository()
    private val games = FakeGamesRepository()

    private val gameLists = FakeGameListUpdates()

    private fun newViewModel() = MatchmakingViewModel(repository, games, gameLists)

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

    // Input: a "Match found!" push while waiting; matchmaking took the player out of the queue with a game
    // Expected output: not searching, the match pop-up shows that game
    @Test
    fun refreshWhileSearchingShowsTheMatch() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(listOf(match()))

        viewModel.refresh()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: the app comes back while the player is still waiting
    // Expected output: still searching, no match
    @Test
    fun refreshWhileSearchingAndStillQueuedKeepsSearching() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(MatchFormat.EITHER)

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
    }

    // Input: a "Match cancelled" push while the pop-up is open
    // Expected output: the pop-up turns into "Match fell through"
    @Test
    fun refreshWithTheMatchOpenShowsFellThroughWhenCancelled() = runBlocking {
        val viewModel = matchedViewModel()
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))

        viewModel.refresh()

        assertNull(viewModel.uiState.value.match)
        assertTrue(viewModel.uiState.value.matchFellThrough)
    }

    // Input: the app opens while the player is waiting for singles (e.g. after a restart)
    // Expected output: searching again, for singles
    @Test
    fun refreshWhenIdleRestoresTheQueue() = runBlocking {
        repository.queuedResult = Result.success(MatchFormat.SINGLES)
        val viewModel = newViewModel()

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(MatchFormat.SINGLES, viewModel.uiState.value.format)
    }

    // Input: the app opens after the player was matched while it was closed
    // Expected output: the match pop-up shows the pending match
    @Test
    fun refreshWhenIdleShowsAPendingMatch() = runBlocking {
        games.myGamesResult = Result.success(listOf(match()))
        val viewModel = newViewModel()

        viewModel.refresh()

        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: tapping "Match found!" brings the app back and delivers the push at once, so two refreshes run
    // Expected output: the pop-up shows the match once, never "Match fell through"
    @Test
    fun refreshTwiceAfterAMatchShowsItOnce() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(listOf(match()))
        games.gameResult = Result.success(match())

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(match(), viewModel.uiState.value.match)
        assertFalse(viewModel.uiState.value.matchFellThrough)
    }

    // Input: the player accepted, tapped "Go to game", then the app comes back while the others haven't answered
    // Expected output: the pop-up stays closed instead of reopening over the game screen
    @Test
    fun refreshAfterGoToGameDoesNotReopenTheMatch() = runBlocking {
        val viewModel = matchedViewModel()
        viewModel.onGoToGame()

        viewModel.refresh()

        assertNull(viewModel.uiState.value.match)
    }

    // Input: the app comes back while queued, and the matchmaking job claims the match between the two lookups
    // Expected output: the pop-up shows the match and the player is no longer searching
    @Test
    fun refreshWhenQueuedAndMatchedStopsSearching() = runBlocking {
        repository.queuedResult = Result.success(MatchFormat.SINGLES)
        games.myGamesResult = Result.success(listOf(match()))
        val viewModel = newViewModel()

        viewModel.refresh()

        assertEquals(match(), viewModel.uiState.value.match)
        assertFalse(viewModel.uiState.value.isSearching)
    }

    // Input: a refresh while waiting fails because the backend is unreachable
    // Expected output: nothing changes
    @Test
    fun refreshFailureChangesNothing() = runBlocking {
        val viewModel = queuedViewModel()
        val before = viewModel.uiState.value
        repository.queuedResult = Result.failure(IOException("offline"))

        viewModel.refresh()

        assertEquals(before, viewModel.uiState.value)
    }

    private val nearbyUbc = NearbyPlayers(MapPoint(49.2606, -123.246), listOf(MapPoint(49.268, -123.168)))
    private val nearbyTwo = NearbyPlayers(
        MapPoint(49.2606, -123.246),
        listOf(MapPoint(49.268, -123.168), MapPoint(49.264, -123.2))
    )

    // Input: waiting in the queue; the stream sends one player nearby, then a second one who joined
    // Expected output: the map follows the pushes and ends with both players, with nothing polled
    @Test
    fun followQueueShowsEachPushOfThePlayersNearby() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queueStream = flowOf(QueueUpdate.Nearby(nearbyUbc), QueueUpdate.Nearby(nearbyTwo))

        viewModel.followQueue()

        assertEquals(nearbyTwo, viewModel.uiState.value.nearby)
    }

    // Input: not in the queue, and the stream sends players nearby anyway
    // Expected output: the map stays empty
    @Test
    fun followQueueIgnoresPushesWhenNotSearching() = runBlocking {
        val viewModel = newViewModel()
        repository.queueStream = flowOf(QueueUpdate.Nearby(nearbyUbc))

        viewModel.followQueue()

        assertNull(viewModel.uiState.value.nearby)
    }

    // Input: dots already shown, then the backend refuses the stream when it reconnects
    // Expected output: the last dots stay, and no error is shown
    @Test
    fun followQueueKeepsTheLastDotsWhenRefused() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queueStream = flow {
            emit(QueueUpdate.Nearby(nearbyUbc))
            throw HttpException(Response.error<Unit>(401, "".toResponseBody()))
        }

        viewModel.followQueue()

        assertEquals(nearbyUbc, viewModel.uiState.value.nearby)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: searching; the stream says the player is no longer queued (matchmaking took them out)
    // Expected behavior: the map stops, nothing else is fetched: the match comes from the live games (followMatches)
    // Expected output: still showing the search, no match, no "fell through"
    @Test
    fun followQueueLeavesTheMatchToTheLiveGames() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(listOf(match()))
        repository.queueStream = flowOf(QueueUpdate.Nearby(nearbyUbc), QueueUpdate.NotQueued)

        viewModel.followQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
        assertFalse(viewModel.uiState.value.matchFellThrough)
    }

    // Input: dots shown during a search, then the player leaves and searches again
    // Expected output: the new search starts with no dots instead of the old search's
    @Test
    fun searchingAgainClearsTheOldDots() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queueStream = flowOf(QueueUpdate.Nearby(nearbyUbc))
        viewModel.followQueue()
        viewModel.onLeaveQueue()

        viewModel.onFindMatch()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.nearby)
    }

    // Input: searching; the live games list pushes the new match, and no push arrives
    // Expected output: not searching, the match pop-up shows that game
    @Test
    fun followMatchesShowsAMatchWhosePushNeverArrived() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(listOf(match()))
        gameLists.stream = flowOf(GameListUpdate.Mine(listOf(match()), emptyList()))

        viewModel.followMatches()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(match(), viewModel.uiState.value.match)
    }

    // Input: searching; the live games list arrives on connect while the player is still in the queue
    // Expected output: still searching, no match
    @Test
    fun followMatchesKeepsSearchingWhileQueued() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(MatchFormat.EITHER)
        gameLists.stream = flowOf(GameListUpdate.Mine(emptyList(), emptyList()))

        viewModel.followMatches()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
    }

    // Input: searching; only the feed (games open to everyone) changes
    // Expected output: still searching: the feed says nothing about this player's match, so it isn't checked
    @Test
    fun followMatchesIgnoresTheFeed() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        games.myGamesResult = Result.success(listOf(match()))
        gameLists.stream = flowOf(GameListUpdate.Feed(listOf(match()), emptyList()))

        viewModel.followMatches()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.match)
    }

    // Input: the match pop-up is open, and the other player accepts it, which changes the user's games
    // Expected output: the pop-up shows the game with the other player's answer
    @Test
    fun followMatchesShowsTheOtherPlayersAnswer() = runBlocking {
        val viewModel = matchedViewModel()
        val answered = match().copy(
            players = match().players.map { if (it.username == "bob") it.copy(acceptedAt = Instant.EPOCH) else it }
        )
        games.gameResult = Result.success(answered)
        gameLists.stream = flowOf(GameListUpdate.Mine(listOf(answered), emptyList()))

        viewModel.followMatches()

        assertEquals(answered, viewModel.uiState.value.match)
    }

    // Input: the match pop-up is open, and the other player cancels the match
    // Expected output: the pop-up closes and "Match fell through" shows
    @Test
    fun followMatchesShowsAMatchCancelledByTheOtherPlayer() = runBlocking {
        val viewModel = matchedViewModel()
        games.gameResult = Result.success(match(status = GameStatus.CANCELLED))
        gameLists.stream = flowOf(GameListUpdate.Mine(emptyList(), emptyList()))

        viewModel.followMatches()

        assertNull(viewModel.uiState.value.match)
        assertTrue(viewModel.uiState.value.matchFellThrough)
    }

    // Input: the backend refuses the live stream
    // Expected behavior: following ends quietly; pushes and returning to the app still catch the match
    // Expected output: still searching
    @Test
    fun followMatchesEndsQuietlyWhenRefused() = runBlocking {
        val viewModel = queuedViewModel()
        gameLists.stream = flow { throw HttpException(Response.error<Unit>(401, "".toResponseBody())) }

        viewModel.followMatches()

        assertTrue(viewModel.uiState.value.isSearching)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: the match is found and shown (its push beat the answer) while the join request is still on its way
    // Expected output: the answer doesn't put the player back to searching, the match pop-up stays
    @Test
    fun aMatchShownWhileJoiningEndsTheSearch() = runBlocking {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        games.myGamesResult = Result.success(listOf(match()))
        repository.duringJoin = { viewModel.loadMatch() }

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(match(), viewModel.uiState.value.match)
    }
}
