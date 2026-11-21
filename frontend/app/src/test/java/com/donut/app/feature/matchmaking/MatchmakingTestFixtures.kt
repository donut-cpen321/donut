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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.time.Instant

/**
 * Stands in for the backend: returns whatever each test sets and records what was sent
 */
class FakeMatchmakingRepository : MatchmakingRepository {
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
class FakeGamesRepository : UnusedGamesRepository() {
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
class FakeGameListUpdates : GameListUpdates {
    var stream: Flow<GameListUpdate> = emptyFlow()

    override fun updates(): Flow<GameListUpdate> = stream
}

// The fixtures every Matchmaking view model test shares: a fresh view model on fake repositories
abstract class MatchmakingViewModelTestBase {
    protected val ubc = MatchLocation("UBC Tennis Centre", 49.2606, -123.2460)
    protected val repository = FakeMatchmakingRepository()
    protected val games = FakeGamesRepository()

    protected val gameLists = FakeGameListUpdates()

    protected fun newViewModel() = MatchmakingViewModel(repository, games, gameLists)

    // A singles match between alice (the signed-in user) and bob, still waiting for answers
    protected fun match(
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
    protected fun matchedViewModel(): MatchmakingViewModel {
        games.myGamesResult = Result.success(listOf(match()))
        val viewModel = newViewModel()
        runBlocking { viewModel.loadMatch() }
        return viewModel
    }

    // A view model already waiting in the queue near UBC
    protected fun queuedViewModel(): MatchmakingViewModel {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        runBlocking { viewModel.onFindMatch() }
        return viewModel
    }
}
