package com.donut.app.core.network

import com.donut.app.core.network.dto.AcceptFriendRequestDto
import com.donut.app.core.network.dto.FcmTokenRequestDto
import com.donut.app.core.network.dto.CourtsResponseDto
import com.donut.app.core.network.dto.CreateEquipmentRequestDto
import com.donut.app.core.network.dto.JoinQueueRequestDto
import com.donut.app.core.network.dto.LeaderboardResponseDto
import com.donut.app.core.network.dto.LoginRequestDto
import com.donut.app.core.network.dto.QueueEntryResponseDto
import com.donut.app.core.network.dto.RefreshRequestDto
import com.donut.app.core.network.dto.SendFriendRequestDto
import com.donut.app.core.network.dto.TokensResponseDto
import com.donut.app.core.network.dto.UpdateEquipmentRequestDto
import com.donut.app.core.network.dto.UserResponseDto
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("user/login")
    suspend fun loginWithBackend(@Body request: LoginRequestDto): UserResponseDto

    @POST("user/refresh")
    suspend fun refreshTokens(@Body request: RefreshRequestDto): TokensResponseDto

    @POST("user/logout")
    suspend fun logout(@Body request: RefreshRequestDto): Response<Unit>

    @GET("user/me")
    suspend fun getUserMe(@Header("Authorization") authorization: String): JsonElement

    @GET("user/{userId}/photo")
    suspend fun getUserPhoto(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
    ): JsonElement

    @GET("user/equipment")
    suspend fun getEquipment(@Header("Authorization") authorization: String): JsonElement

    @POST("user/equipment")
    suspend fun createEquipment(
        @Header("Authorization") authorization: String,
        @Body request: CreateEquipmentRequestDto,
    ): JsonElement

    @PATCH("user/equipment/{id}")
    suspend fun updateEquipment(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body request: UpdateEquipmentRequestDto,
    ): JsonElement

    @DELETE("user/equipment/{id}")
    suspend fun deleteEquipment(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
    ): Response<ResponseBody>
    @PUT("user/me/fcm-token")
    suspend fun saveFcmToken(
        @Header("Authorization") authorization: String,
        @Body request: FcmTokenRequestDto
    )

    @GET("matchmaking")
    suspend fun getQueueEntry(@Header("Authorization") authorization: String): QueueEntryResponseDto

    @POST("matchmaking")
    suspend fun joinQueue(
        @Header("Authorization") authorization: String,
        @Body request: JoinQueueRequestDto
    ): QueueEntryResponseDto

    @DELETE("matchmaking")
    suspend fun leaveQueue(@Header("Authorization") authorization: String)

    @GET("friends")
    suspend fun getFriends(@Header("Authorization") authorization: String): JsonElement

    // Removes a friend, or declines or cancels a pending request
    @DELETE("friends")
    suspend fun removeFriend(
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String,
    ): Response<ResponseBody>

    @GET("friends/requests")
    suspend fun getFriendRequests(@Header("Authorization") authorization: String): JsonElement

    @POST("friends/requests/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: AcceptFriendRequestDto,
    ): Response<ResponseBody>

    // Sends a friend request, or accepts the other user's pending request to this user
    @POST("friends/requests")
    suspend fun sendFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: SendFriendRequestDto,
    ): Response<ResponseBody>

    @GET("leaderboard")
    suspend fun getLeaderboard(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int? = null,
    ): LeaderboardResponseDto

    @GET("courts")
    suspend fun getCourts(@Header("Authorization") authorization: String): CourtsResponseDto

    @GET("user/me/preferred-courts/")
    suspend fun getPreferredCourts(@Header("Authorization") authorization: String): JsonElement

    @PUT("user/me/preferred-courts/{courtId}")
    suspend fun addPreferredCourt(
        @Header("Authorization") authorization: String,
        @Path("courtId") courtId: Int,
    ): Response<ResponseBody>

    @DELETE("user/me/preferred-courts/{courtId}")
    suspend fun removePreferredCourt(
        @Header("Authorization") authorization: String,
        @Path("courtId") courtId: Int,
    ): Response<ResponseBody>
}
