package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.AcceptFriendRequestDto
import com.example.cpen321application.core.network.dto.LoginRequestDto
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import com.example.cpen321application.core.network.dto.TokensResponseDto
import com.example.cpen321application.core.network.dto.UserResponseDto
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("user/login")
    suspend fun loginWithBackend(@Body request: LoginRequestDto): UserResponseDto

    @POST("user/refresh")
    suspend fun refreshTokens(@Body request: RefreshRequestDto): TokensResponseDto

    @POST("user/logout")
    suspend fun logout(@Body request: RefreshRequestDto): Response<Unit>

    @GET("friends")
    suspend fun getFriends(@Header("Authorization") authorization: String): JsonElement

    @DELETE("friends/{userId}")
    suspend fun removeFriend(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<ResponseBody>

    @GET("friends/requests")
    suspend fun getFriendRequests(@Header("Authorization") authorization: String): JsonElement

    @POST("friends/requests/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: AcceptFriendRequestDto
    ): Response<ResponseBody>

    // TODO: Implement getMatchHistory (prob games/smth)
    @GET("matches/history")
    suspend fun getMatchHistory(@Header("Authorization") authorization: String): JsonElement
}
