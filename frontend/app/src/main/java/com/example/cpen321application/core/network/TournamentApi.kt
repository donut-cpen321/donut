package com.example.cpen321application.core.network

import com.example.cpen321application.core.network.dto.CreateTournamentRequestDto
import com.example.cpen321application.core.network.dto.CreatedTournamentResponseDto
import com.example.cpen321application.core.network.dto.TournamentIdRequestDto
import com.example.cpen321application.core.network.dto.TournamentResponseDto
import com.example.cpen321application.core.network.dto.TournamentsResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The /tournaments routes. Separate from ApiService, which already has many routes.
 * Called through AuthSession.authorized, so routes without a body return Unit and throw HttpException on errors.
 */
interface TournamentApi {
    @POST("tournaments")
    suspend fun createTournament(
        @Header("Authorization") authorization: String,
        @Body request: CreateTournamentRequestDto
    ): CreatedTournamentResponseDto

    @GET("tournaments/feed")
    suspend fun getTournamentFeed(@Header("Authorization") authorization: String): TournamentsResponseDto

    @GET("tournaments/me")
    suspend fun getMyTournaments(@Header("Authorization") authorization: String): TournamentsResponseDto

    @GET("tournaments")
    suspend fun getTournament(
        @Header("Authorization") authorization: String,
        @Query("tournamentId") tournamentId: String
    ): TournamentResponseDto

    @POST("tournaments/join")
    suspend fun joinTournament(
        @Header("Authorization") authorization: String,
        @Body request: TournamentIdRequestDto
    )

    @POST("tournaments/leave")
    suspend fun leaveTournament(
        @Header("Authorization") authorization: String,
        @Body request: TournamentIdRequestDto
    )

    @DELETE("tournaments")
    suspend fun cancelTournament(
        @Header("Authorization") authorization: String,
        @Query("tournamentId") tournamentId: String
    )
}
