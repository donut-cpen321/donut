package com.donut.app.core.network

import com.donut.app.core.network.dto.PlayerStatsResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * The /stats route. Separate from ApiService, which already has many routes.
 * Called through AuthSession.authorized, so it throws HttpException on errors.
 */
interface StatsApi {
    // A null userId is left out of the query, so the backend returns the caller's own stats
    @GET("stats")
    suspend fun getStats(
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String? = null
    ): PlayerStatsResponseDto
}
