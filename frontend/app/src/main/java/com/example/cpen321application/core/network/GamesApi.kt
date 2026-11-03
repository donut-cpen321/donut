package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.AcceptMatchRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameLocationRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameTimeRequestDto
import com.example.cpen321application.core.network.dto.CreateGameRequestDto
import com.example.cpen321application.core.network.dto.CreatedGameResponseDto
import com.example.cpen321application.core.network.dto.GameResponseDto
import com.example.cpen321application.core.network.dto.GamesResponseDto
import com.example.cpen321application.core.network.dto.InviteFriendRequestDto
import com.example.cpen321application.core.network.dto.InviteFriendResponseDto
import com.example.cpen321application.core.network.dto.JoinGameRequestDto
import com.example.cpen321application.core.network.dto.LeaveGameRequestDto
import com.example.cpen321application.core.network.dto.ReportScoreRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The /games routes. Separate from ApiService, which already has many routes.
 * Called through AuthSession.authorized, so routes without a body return Unit (not Response<Unit>)
 * and throw HttpException on errors.
 */
interface GamesApi {
    // The signed-in user's completed games, most recent first
    @GET("games/history")
    suspend fun getGameHistory(@Header("Authorization") authorization: String): GamesResponseDto

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

    @POST("games/accept")
    suspend fun acceptMatch(
        @Header("Authorization") authorization: String,
        @Body request: AcceptMatchRequestDto
    )

    // Sends a friend a push notification inviting them to the game
    @POST("games/invite")
    suspend fun inviteToGame(
        @Header("Authorization") authorization: String,
        @Body request: InviteFriendRequestDto
    ): InviteFriendResponseDto

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
}
