package com.example.cpen321application.feature.matchmaking

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.dto.JoinQueueRequestDto
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection

/**
 * The player's place in the matchmaking queue on the backend
 */
interface MatchmakingRepository {
    /**
     * Joins the queue. Succeeds if the player was already waiting.
     */
    suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit>

    suspend fun leaveQueue(): Result<Unit>

    /**
     * @return the format the player is waiting for, null if they are not waiting (never joined, left, or matched)
     */
    suspend fun queuedFormat(): Result<MatchFormat?>
}

class MatchmakingRepositoryImpl(
    private val session: AuthSession,
    private val api: ApiService = RetrofitClient.apiService
) : MatchmakingRepository {

    override suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit> {
        val request = JoinQueueRequestDto(format.name, location.latitude, location.longitude)
        val result = call { authorization -> api.joinQueue(authorization, request) }
        // Already waiting, e.g. joined before the app restarted
        if (result.httpCode() == HttpURLConnection.HTTP_CONFLICT) {
            return Result.success(Unit)
        }
        return result.map { }
    }

    override suspend fun leaveQueue(): Result<Unit> {
        return call { authorization -> api.leaveQueue(authorization) }
    }

    override suspend fun queuedFormat(): Result<MatchFormat?> {
        val result = call { authorization -> api.getQueueEntry(authorization) }
        if (result.httpCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return Result.success(null)
        }
        return result.map { MatchFormat.valueOf(it.request.format) }
    }

    /**
     * Calls a route with the signed-in user's token
     * @return the route's result, or a failure if the backend rejected it, is unreachable, or nobody is signed in
     */
    private suspend fun <T> call(route: suspend (authorization: String) -> T): Result<T> {
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
}
