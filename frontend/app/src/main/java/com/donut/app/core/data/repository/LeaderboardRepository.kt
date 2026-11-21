package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.dto.LeaderboardResponseDto
import retrofit2.HttpException
import java.io.IOException

interface LeaderboardRepository {
    /**
     * @param session the signed-in user's session
     * @return the top players and the caller's own entry
     */
    suspend fun getLeaderboard(session: AuthSession): Result<LeaderboardResponseDto>
}

class LeaderboardRepositoryImpl(
    private val apiService: ApiService = RetrofitClient.apiService,
) : LeaderboardRepository {

    override suspend fun getLeaderboard(session: AuthSession): Result<LeaderboardResponseDto> {
        return try {
            val response = session.authorized { auth ->
                apiService.getLeaderboard(auth)
            }
            if (response == null) {
                Result.failure(Exception("Not authenticated"))
            } else {
                Result.success(response)
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
