package com.donut.app.feature.gamedetail

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.data.repository.CourtsRepository
import com.donut.app.core.data.repository.GameUpdate
import com.donut.app.feature.profile.FakeFriendsRepository
import com.donut.app.testing.UnusedGamesRepository
import com.donut.app.testing.signedInSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

// Only gameUpdates is used: the screen's live updates
private class FakeGamesRepository(val updates: Flow<GameUpdate>) : UnusedGamesRepository() {
    override fun gameUpdates(gameId: String): Flow<GameUpdate> = updates
}

private object UnusedCourts : CourtsRepository {
    override suspend fun getCourts(): Result<List<Court>> = error("unused")
}

// A test that hangs fails instead of stalling the whole build
private const val TEST_TIMEOUT_MS = 10_000L

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailFollowTest {
    // The view model's own scope (its actions timer) runs on a real background thread, not a test dispatcher.
    // The timer waits until a time on the real clock; on a test dispatcher runTest would skip its virtual waits
    // and run it again and again forever, since the real clock never reaches the time. On a real thread a timer
    // hours away just waits, and runTest doesn't wait for it
    private val mainThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // The stream connects through the fake, so the session's API is never called
    private val session = signedInSession("bob")

    private val alice = Player("u1", "alice", "Alice A", 1500.0, 1)

    private fun game(startsIn: Duration = Duration.ofHours(2), status: GameStatus = GameStatus.OPEN) = Game(
        id = "game-1",
        format = Format.SINGLES,
        status = status,
        source = GameSource.SCHEDULED,
        startsAt = Instant.now().plus(startsIn),
        court = Court(1, "Stanley Park", 49.3, -123.14),
        players = listOf(alice),
        rounds = emptyList()
    )

    // Viewed by bob, who isn't in the game
    private fun viewModel(updates: Flow<GameUpdate>) = GameDetailViewModel(
        gameId = "game-1",
        gamesRepository = FakeGamesRepository(updates),
        courtsRepository = UnusedCourts,
        currentUsername = { "bob" },
        friendsRepository = FakeFriendsRepository(),
        session = session
    )

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(mainThread)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
        mainThread.close()
    }

    // Input: the screen opens (loading), then the backend pushes the game
    // Expected output: the game and its actions are shown, and it is no longer loading
    @Test(timeout = TEST_TIMEOUT_MS)
    fun pushedGameIsShown() = runTest {
        val pushed = game()
        val viewModel = viewModel(flowOf(GameUpdate.Changed(pushed)))
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.followUpdates()

        val state = viewModel.uiState.value
        assertEquals(pushed, state.game)
        assertEquals(listOf(2), state.actions?.joinableTeams)
        assertFalse(state.isLoading)
    }

    // Input: the backend pushes that the game was deleted (cancelled)
    // Expected output: the game is gone and the screen says it was cancelled
    @Test(timeout = TEST_TIMEOUT_MS)
    fun deletedGameSaysItWasCancelled() = runTest {
        val viewModel = viewModel(flowOf(GameUpdate.Changed(game()), GameUpdate.Deleted))

        viewModel.followUpdates()

        assertNull(viewModel.uiState.value.game)
        assertEquals("This game was cancelled.", viewModel.uiState.value.errorMessage)
    }

    // Input: the first connection drops, the second pushes the game
    // Expected behavior: it reconnects after the delay
    // Expected output: two connections, and the game shown
    @Test(timeout = TEST_TIMEOUT_MS)
    fun reconnectsAfterADroppedConnection() = runTest {
        var connections = 0
        val pushed = game()
        val viewModel = viewModel(
            flow {
                if (connections++ == 0) throw IOException("connection dropped")
                emit(GameUpdate.Changed(pushed))
            }
        )

        viewModel.followUpdates()

        assertEquals(2, connections)
        assertEquals(pushed, viewModel.uiState.value.game)
    }

    // Input: the backend refuses the stream with 404
    // Expected output: it stops loading and shows the error, without crashing
    @Test(timeout = TEST_TIMEOUT_MS)
    fun refusedStreamShowsTheError() = runTest {
        val viewModel = viewModel(flow { throw HttpException(Response.error<Unit>(404, "".toResponseBody())) })

        viewModel.followUpdates()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Request failed (HTTP 404)", viewModel.uiState.value.errorMessage)
    }

    // Input: an open game that starts in 200 ms, shown while it can still be joined
    // Expected behavior: when its start time passes, the actions are recomputed without the backend
    // Expected output: joinable before, not joinable after
    @Test(timeout = TEST_TIMEOUT_MS)
    fun actionsChangeWhenTheStartTimePasses() = runTest {
        val viewModel = viewModel(flowOf(GameUpdate.Changed(game(startsIn = Duration.ofMillis(300)))))

        viewModel.followUpdates()
        assertEquals(listOf(2), viewModel.uiState.value.actions?.joinableTeams)

        // The timer fires on the real thread once the start time has passed. Waits for its new state in real time
        // (runTest would skip a delay), up to 3 s
        val afterStart = withContext(Dispatchers.Default) {
            withTimeout(3_000) {
                viewModel.uiState.first { it.actions?.joinableTeams == emptyList<Int>() }
            }
        }

        assertEquals(emptyList<Int>(), afterStart.actions?.joinableTeams)
    }
}
