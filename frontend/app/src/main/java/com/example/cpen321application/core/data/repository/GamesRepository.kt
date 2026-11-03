package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.AcceptMatchRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameLocationRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameTimeRequestDto
import com.example.cpen321application.core.network.dto.CreateGameRequestDto
import com.example.cpen321application.core.network.dto.InviteFriendRequestDto
import com.example.cpen321application.core.network.dto.JoinGameRequestDto
import com.example.cpen321application.core.network.dto.LeaveGameRequestDto
import com.example.cpen321application.core.network.dto.ReportScoreRequestDto
import com.example.cpen321application.core.network.dto.toGame
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

interface GamesRepository {
    // Scheduled games with free spots that have not started, with their forecast
    suspend fun getFeed(): Result<List<Game>>

    // The signed-in user's booked games, and open games that have not started
    suspend fun getMyGames(): Result<List<Game>>

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

    suspend fun changeGameTime(gameId: String, startsAt: Instant): Result<Unit>

    suspend fun changeGameLocation(gameId: String, courtId: Int): Result<Unit>

    // Ends the game with its final score: the (team 1, team 2) score of each round, in order
    suspend fun reportScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit>
}

class GamesRepositoryImpl(
    private val session: AuthSession,
    private val apiService: ApiService = RetrofitClient.apiService
) : GamesRepository {

    override suspend fun getFeed(): Result<List<Game>> =
        request { apiService.getGameFeed(it).games.map { game -> game.toGame() } }

    override suspend fun getMyGames(): Result<List<Game>> =
        request { apiService.getMyGames(it).games.map { game -> game.toGame() } }

    override suspend fun getGame(gameId: String): Result<Game> =
        request { apiService.getGame(it, gameId).game.toGame() }

    override suspend fun createGame(format: Format, startsAt: Instant, courtId: Int): Result<String> =
        request {
            val body = CreateGameRequestDto(
                format = format.name,
                startsAt = toIsoString(startsAt),
                courtId = courtId,
                gameSource = GameSource.SCHEDULED.name
            )
            apiService.createGame(it, body).game.id
        }

    override suspend fun cancelGame(gameId: String): Result<Unit> =
        request { apiService.cancelGame(it, gameId) }

    override suspend fun joinGame(gameId: String, team: Int): Result<Unit> =
        request { apiService.joinGame(it, JoinGameRequestDto(gameId, team)) }

    override suspend fun leaveGame(gameId: String): Result<Unit> =
        request { apiService.leaveGame(it, LeaveGameRequestDto(gameId)) }

    override suspend fun acceptMatch(gameId: String): Result<Unit> =
        request { apiService.acceptMatch(it, AcceptMatchRequestDto(gameId)) }

    override suspend fun inviteFriend(gameId: String, userId: String): Result<Boolean> =
        request { apiService.inviteToGame(it, InviteFriendRequestDto(gameId, userId)).notified }

    override suspend fun changeGameTime(gameId: String, startsAt: Instant): Result<Unit> =
        request { apiService.changeGameTime(it, ChangeGameTimeRequestDto(gameId, toIsoString(startsAt))) }

    override suspend fun changeGameLocation(gameId: String, courtId: Int): Result<Unit> =
        request { apiService.changeGameLocation(it, ChangeGameLocationRequestDto(gameId, courtId)) }

    override suspend fun reportScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit> =
        request {
            val scores = rounds.map { (team1Score, team2Score) -> listOf(team1Score, team2Score) }
            apiService.reportScore(it, ReportScoreRequestDto(gameId, scores))
        }

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
