package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.StatsApi
import com.donut.app.core.network.dto.PlayerStatsDto
import retrofit2.HttpException
import java.io.IOException

interface StatsRepository {
    /**
     * @param userId the player whose stats are requested, null for the signed-in user
     * @return the player's rating, totals, rating history and results by opponent rating
     */
    suspend fun getStats(userId: String? = null): Result<PlayerStatsDto>
}

class StatsRepositoryImpl(
    private val session: AuthSession,
    private val api: StatsApi = RetrofitClient.statsApi,
) : StatsRepository {

    override suspend fun getStats(userId: String?): Result<PlayerStatsDto> {
        return try {
            val stats = session.authorized { auth -> api.getStats(auth, userId) }?.stats
            if (stats == null) {
                // The app goes back to Login on its own once the user is signed out
                Result.failure(IllegalStateException("You are signed out."))
            } else {
                Result.success(stats)
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
