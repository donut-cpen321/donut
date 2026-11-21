package com.donut.app.feature.matchmaking

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.repository.liveEvents
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.ServerSentEvent
import com.donut.app.core.network.dto.JoinQueueRequestDto
import com.donut.app.core.network.dto.NearbyPlayersDto
import com.donut.app.core.network.dto.PointDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection

// The player has a matched game waiting for answers, or booked and not started, so they can't queue again
class ActiveMatchException : Exception()

/**
 * The player's place in the matchmaking queue on the backend
 */
interface MatchmakingRepository {
    /**
     * Joins the queue. Succeeds if the player was already waiting.
     * Fails with ActiveMatchException if the player already has a match coming up.
     */
    suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit>

    suspend fun leaveQueue(): Result<Unit>

    /**
     * @return the format the player is waiting for, null if they are not waiting (never joined, left, or matched)
     */
    suspend fun queuedFormat(): Result<MatchFormat?>

    /**
     * The player's queue pushed by the backend (GET /matchmaking/events): the players searching near them first,
     * then again whenever anyone joins or leaves the queue or is matched, or NotQueued once the player isn't in it.
     * Ends right away if signed out. Throws IOException if the connection drops, HttpException if refused
     */
    fun queueUpdates(): Flow<QueueUpdate>
}

// What the backend pushes about the player's place in the queue
sealed interface QueueUpdate {
    data class Nearby(val players: NearbyPlayers) : QueueUpdate

    // Left the queue, or matchmaking took the player out of it with a match
    data object NotQueued : QueueUpdate
}

class MatchmakingRepositoryImpl(
    private val session: AuthSession,
    private val api: ApiService = RetrofitClient.apiService,
    private val eventStreamApi: EventStreamApi = RetrofitClient.eventStreamApi,
    private val gson: Gson = Gson()
) : MatchmakingRepository {

    override suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit> {
        val request = JoinQueueRequestDto(format.name, location.latitude, location.longitude)
        val result = callWithToken { authorization -> api.joinQueue(authorization, request) }
        // Already waiting, e.g. joined before the app restarted
        if (result.httpCode() == HttpURLConnection.HTTP_CONFLICT) {
            return Result.success(Unit)
        }
        if (result.httpCode() == HttpURLConnection.HTTP_FORBIDDEN) {
            return Result.failure(ActiveMatchException())
        }
        return result.map { }
    }

    override suspend fun leaveQueue(): Result<Unit> {
        return callWithToken { authorization -> api.leaveQueue(authorization) }
    }

    override suspend fun queuedFormat(): Result<MatchFormat?> {
        val result = callWithToken { authorization -> api.getQueueEntry(authorization) }
        if (result.httpCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return Result.success(null)
        }
        return result.map { MatchFormat.valueOf(it.request.format) }
    }

    override fun queueUpdates(): Flow<QueueUpdate> =
        session.liveEvents(eventStreamApi, "matchmaking/events", read = ::toUpdate)

    // Null for events this version of the app doesn't know
    private fun toUpdate(event: ServerSentEvent): QueueUpdate? =
        when (event.name) {
            "nearby" -> gson.fromJson(event.data, NearbyPlayersDto::class.java).let {
                QueueUpdate.Nearby(NearbyPlayers(it.center.toMapPoint(), it.players.map { point -> point.toMapPoint() }))
            }
            "notQueued" -> QueueUpdate.NotQueued
            else -> null
        }

    // Fails if nobody is signed in, the backend rejected the call, or it is unreachable
    private suspend fun <T> callWithToken(route: suspend (authorization: String) -> T): Result<T> {
        return try {
            val body = session.authorized(route)
            if (body == null) {
                Result.failure(IllegalStateException("Not signed in"))
            } else {
                Result.success(body)
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun Result<*>.httpCode(): Int? = (exceptionOrNull() as? HttpException)?.code()

    private fun PointDto.toMapPoint(): MapPoint {
        return MapPoint(latitude, longitude)
    }
}
