package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.AcceptFriendRequestDto
import com.example.cpen321application.core.network.dto.FcmTokenRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameLocationRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameTimeRequestDto
import com.example.cpen321application.core.network.dto.CourtsResponseDto
import com.example.cpen321application.core.network.dto.CreateEquipmentRequestDto
import com.example.cpen321application.core.network.dto.CreateGameRequestDto
import com.example.cpen321application.core.network.dto.CreatedGameResponseDto
import com.example.cpen321application.core.network.dto.GameResponseDto
import com.example.cpen321application.core.network.dto.GamesResponseDto
import com.example.cpen321application.core.network.dto.JoinGameRequestDto
import com.example.cpen321application.core.network.dto.JoinQueueRequestDto
import com.example.cpen321application.core.network.dto.LeaderboardResponseDto
import com.example.cpen321application.core.network.dto.LeaveGameRequestDto
import com.example.cpen321application.core.network.dto.LoginRequestDto
import com.example.cpen321application.core.network.dto.QueueEntryResponseDto
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import com.example.cpen321application.core.network.dto.ReportScoreRequestDto
import com.example.cpen321application.core.network.dto.TokensResponseDto
import com.example.cpen321application.core.network.dto.UpdateEquipmentRequestDto
import com.example.cpen321application.core.network.dto.UserResponseDto
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

    @DELETE("friends/{userId}")
    suspend fun removeFriend(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
    ): Response<ResponseBody>

    @GET("friends/requests")
    suspend fun getFriendRequests(@Header("Authorization") authorization: String): JsonElement

    @POST("friends/requests/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") authorization: String,
        @Body request: AcceptFriendRequestDto,
    ): Response<ResponseBody>

    // TODO: Implement getMatchHistory (prob games/smth)
    @GET("matches/history")
    suspend fun getMatchHistory(@Header("Authorization") authorization: String): JsonElement

    @GET("leaderboard")
    suspend fun getLeaderboard(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int? = null,
    ): LeaderboardResponseDto

    // Games. Called through AuthSession.authorized, so routes without a body return Unit
    // (not Response<Unit>) and throw HttpException on errors.

    @GET("games/feed")
    suspend fun getGameFeed(@Header("Authorization") authorization: String): GamesResponseDto

    @GET("games/me")
    suspend fun getMyGames(@Header("Authorization") authorization: String): GamesResponseDto

    @GET("games")
    suspend fun getGame(
        @Header("Authorization") authorization: String,
        @Query("gameId") gameId: String
    ): GameResponseDto

    @POST("games")
    suspend fun createGame(
        @Header("Authorization") authorization: String,
        @Body request: CreateGameRequestDto
    ): CreatedGameResponseDto

    @DELETE("games")
    suspend fun cancelGame(
        @Header("Authorization") authorization: String,
        @Query("gameId") gameId: String
    )

    @POST("games/join")
    suspend fun joinGame(
        @Header("Authorization") authorization: String,
        @Body request: JoinGameRequestDto
    )

    @POST("games/leave")
    suspend fun leaveGame(
        @Header("Authorization") authorization: String,
        @Body request: LeaveGameRequestDto
    )

    @PATCH("games/time")
    suspend fun changeGameTime(
        @Header("Authorization") authorization: String,
        @Body request: ChangeGameTimeRequestDto
    )

    @PATCH("games/location")
    suspend fun changeGameLocation(
        @Header("Authorization") authorization: String,
        @Body request: ChangeGameLocationRequestDto
    )

    @POST("games/score")
    suspend fun reportScore(
        @Header("Authorization") authorization: String,
        @Body request: ReportScoreRequestDto
    )

    @GET("courts")
    suspend fun getCourts(@Header("Authorization") authorization: String): CourtsResponseDto
}
