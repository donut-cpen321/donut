package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.JoinQueueRequestDto
import com.example.cpen321application.core.network.dto.LoginRequestDto
import com.example.cpen321application.core.network.dto.QueueEntryResponseDto
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import com.example.cpen321application.core.network.dto.TokensResponseDto
import com.example.cpen321application.core.network.dto.UserResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("user/login")
    suspend fun loginWithBackend(@Body request: LoginRequestDto): UserResponseDto

    @POST("user/refresh")
    suspend fun refreshTokens(@Body request: RefreshRequestDto): TokensResponseDto

    @POST("user/logout")
    suspend fun logout(@Body request: RefreshRequestDto): Response<Unit>

    // Matchmaking queue, called through AuthSession.authorized

    @POST("matchmaking")
    suspend fun joinQueue(
        @Header("Authorization") authorization: String,
        @Body request: JoinQueueRequestDto
    ): QueueEntryResponseDto

    @DELETE("matchmaking")
    suspend fun leaveQueue(@Header("Authorization") authorization: String)

    // 404 when the player is not waiting: never joined, left, or was matched
    @GET("matchmaking")
    suspend fun getQueueEntry(@Header("Authorization") authorization: String): QueueEntryResponseDto
}
