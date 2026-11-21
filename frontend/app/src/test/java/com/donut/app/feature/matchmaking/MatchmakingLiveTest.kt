package com.donut.app.feature.matchmaking

import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.repository.GameListUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

// Refreshing after a push, and following the queue and the player's games live
class MatchmakingLiveTest : MatchmakingViewModelTestBase() {
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
