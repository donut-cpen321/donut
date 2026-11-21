package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.GamesApi
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.ServerSentEvent
import com.donut.app.core.network.dto.GameResponseDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile
import com.donut.app.core.network.dto.AcceptMatchRequestDto
import com.donut.app.core.network.dto.ChangeGameLocationRequestDto
import com.donut.app.core.network.dto.ChangeGameTimeRequestDto
import com.donut.app.core.network.dto.CreateGameRequestDto
import com.donut.app.core.network.dto.InviteFriendRequestDto
import com.donut.app.core.network.dto.JoinGameRequestDto
import com.donut.app.core.network.dto.LeaveGameRequestDto
import com.donut.app.core.network.dto.ReportScoreRequestDto
import com.donut.app.core.network.dto.StartGameRequestDto
import com.donut.app.core.network.dto.toGame
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

interface GamesRepository {
    // Scheduled games with free spots that have not started, with their forecast
    suspend fun getFeed(): Result<List<Game>>

    // The signed-in user's booked games, and open games that have not started, with their forecast
    suspend fun getMyGames(): Result<List<Game>>

    // One game with its players and rounds, and its forecast if it is still to be played
    suspend fun getGame(gameId: String): Result<Game>

    // Creates a scheduled game with the signed-in user on team 1, and returns its ID
    suspend fun createGame(format: Format, startsAt: Instant, courtId: Int): Result<String>

    // Deletes the game for every player. A matched game is marked cancelled instead
    suspend fun cancelGame(gameId: String): Result<Unit>

    suspend fun joinGame(gameId: String, team: Int): Result<Unit>

    suspend fun leaveGame(gameId: String): Result<Unit>

    // Accepts a matched game for the signed-in user. The game is booked once every player has
    suspend fun acceptMatch(gameId: String): Result<Unit>

    // Invites a friend to the game with a push notification. True if their device was notified
    suspend fun inviteFriend(gameId: String, userId: String): Result<Boolean>

    // Starts a booked game now, before its start time, so its score can be reported
    suspend fun startGame(gameId: String): Result<Unit>

    suspend fun changeGameTime(gameId: String, startsAt: Instant): Result<Unit>

    suspend fun changeGameLocation(gameId: String, courtId: Int): Result<Unit>

    // Reports the final score: the (team 1, team 2) score of each round, in order. It replaces any score waiting,
    // and the game ends once every player confirmed it
    suspend fun reportScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit>

    // Agrees with the score waiting, sent back as the user saw it, so a score changed since then is refused
    suspend fun confirmScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit>

    // The game pushed by the backend each time anyone changes it, starting with its current state. Ends after
    // Deleted, or right away if signed out. Throws IOException if the connection drops, HttpException if refused
    fun gameUpdates(gameId: String): Flow<GameUpdate>
}

// What the backend pushes about a followed game
sealed interface GameUpdate {
    data class Changed(val game: Game) : GameUpdate

    // The game was cancelled and deleted
    data object Deleted : GameUpdate
}

class GamesRepositoryImpl(
    private val session: AuthSession,
    private val gamesApi: GamesApi = RetrofitClient.gamesApi,
    private val eventStreamApi: EventStreamApi = RetrofitClient.eventStreamApi
) : GamesRepository {
    private val gson = Gson()

    override fun gameUpdates(gameId: String): Flow<GameUpdate> =
        session.liveEvents(eventStreamApi, "games/events", mapOf("gameId" to gameId), ::toGameUpdate)
            .transformWhile { update ->
                emit(update)
                update !is GameUpdate.Deleted
            }

    // Null for events this version of the app doesn't know
    private fun toGameUpdate(event: ServerSentEvent): GameUpdate? =
        when (event.name) {
            "gameUpdated" -> GameUpdate.Changed(gson.fromJson(event.data, GameResponseDto::class.java).game.toGame())
            "gameDeleted" -> GameUpdate.Deleted
            else -> null
        }

    override suspend fun getFeed(): Result<List<Game>> =
        request { gamesApi.getGameFeed(it).games.map { game -> game.toGame() } }

    override suspend fun getMyGames(): Result<List<Game>> =
        request { gamesApi.getMyGames(it).games.map { game -> game.toGame() } }

    override suspend fun getGame(gameId: String): Result<Game> =
        request { gamesApi.getGame(it, gameId).game.toGame() }

    override suspend fun createGame(format: Format, startsAt: Instant, courtId: Int): Result<String> =
        request {
            val body = CreateGameRequestDto(
                format = format.name,
                startsAt = toIsoString(startsAt),
                courtId = courtId,
                gameSource = GameSource.SCHEDULED.name
            )
            gamesApi.createGame(it, body).game.id
        }

    override suspend fun cancelGame(gameId: String): Result<Unit> =
        request { gamesApi.cancelGame(it, gameId) }

    override suspend fun joinGame(gameId: String, team: Int): Result<Unit> =
        request { gamesApi.joinGame(it, JoinGameRequestDto(gameId, team)) }

    override suspend fun leaveGame(gameId: String): Result<Unit> =
        request { gamesApi.leaveGame(it, LeaveGameRequestDto(gameId)) }

    override suspend fun acceptMatch(gameId: String): Result<Unit> =
        request { gamesApi.acceptMatch(it, AcceptMatchRequestDto(gameId)) }

    override suspend fun inviteFriend(gameId: String, userId: String): Result<Boolean> =
        request { gamesApi.inviteToGame(it, InviteFriendRequestDto(gameId, userId)).notified }

    override suspend fun startGame(gameId: String): Result<Unit> =
        request { gamesApi.startGame(it, StartGameRequestDto(gameId)) }

    override suspend fun changeGameTime(gameId: String, startsAt: Instant): Result<Unit> =
        request { gamesApi.changeGameTime(it, ChangeGameTimeRequestDto(gameId, toIsoString(startsAt))) }

    override suspend fun changeGameLocation(gameId: String, courtId: Int): Result<Unit> =
        request { gamesApi.changeGameLocation(it, ChangeGameLocationRequestDto(gameId, courtId)) }

    override suspend fun reportScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit> =
        request { gamesApi.reportScore(it, scoreRequest(gameId, rounds)) }

    override suspend fun confirmScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit> =
        request { gamesApi.confirmScore(it, scoreRequest(gameId, rounds)) }

    private fun scoreRequest(gameId: String, rounds: List<Pair<Int, Int>>) =
        ReportScoreRequestDto(gameId, rounds.map { (team1Score, team2Score) -> listOf(team1Score, team2Score) })

    // Sends an authorized request and wraps its result, like AuthRepository does
    private suspend fun <T> request(call: suspend (authorization: String) -> T): Result<T> {
        return try {
            val result = session.authorized(call)
            if (result == null) {
                // The app goes back to Login on its own once the user is signed out
                Result.failure(IllegalStateException("You are signed out."))
            } else {
                Result.success(result)
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}

// e.g. 2026-11-02T18:00:00Z. The backend requires a time zone, and UTC ("Z") is one
private fun toIsoString(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)
