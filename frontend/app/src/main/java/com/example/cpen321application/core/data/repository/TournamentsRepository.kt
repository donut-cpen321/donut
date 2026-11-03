package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.network.RetrofitClient
import com.example.cpen321application.core.network.TournamentApi
import com.example.cpen321application.core.network.dto.CreateTournamentRequestDto
import com.example.cpen321application.core.network.dto.TournamentIdRequestDto
import com.example.cpen321application.core.network.dto.toTournament
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

interface TournamentsRepository {
    // Tournaments open for registration that have not started, soonest first, with their forecast
    suspend fun getFeed(): Result<List<Tournament>>

    // The signed-in user's tournaments: open ones, ones in progress, and ones completed in the last 24 hours
    suspend fun getMyTournaments(): Result<List<Tournament>>

    // One tournament with its bracket
    suspend fun getTournament(tournamentId: String): Result<Tournament>

    // Creates a tournament with the signed-in user registered, and returns its ID
    suspend fun createTournament(name: String, format: Format, startsAt: Instant, courtId: Int): Result<String>

    // Registers the signed-in user. The backend draws the bracket when this fills the tournament
    suspend fun joinTournament(tournamentId: String): Result<Unit>

    suspend fun leaveTournament(tournamentId: String): Result<Unit>

    // Cancels the tournament for every player. Only its creator can, while registration is open
    suspend fun cancelTournament(tournamentId: String): Result<Unit>
}

class TournamentsRepositoryImpl(
    private val session: AuthSession,
    private val api: TournamentApi = RetrofitClient.tournamentApi
) : TournamentsRepository {

    override suspend fun getFeed(): Result<List<Tournament>> =
        request { api.getTournamentFeed(it).tournaments.map { tournament -> tournament.toTournament() } }

    override suspend fun getMyTournaments(): Result<List<Tournament>> =
        request { api.getMyTournaments(it).tournaments.map { tournament -> tournament.toTournament() } }

    override suspend fun getTournament(tournamentId: String): Result<Tournament> =
        request { api.getTournament(it, tournamentId).tournament.toTournament() }

    override suspend fun createTournament(
        name: String,
        format: Format,
        startsAt: Instant,
        courtId: Int
    ): Result<String> =
        request {
            val body = CreateTournamentRequestDto(
                name = name,
                format = format.name,
                // e.g. 2026-11-08T17:00:00Z. The backend requires a time zone, and UTC ("Z") is one
                startsAt = DateTimeFormatter.ISO_INSTANT.format(startsAt),
                courtId = courtId
            )
            api.createTournament(it, body).tournament.id
        }

    override suspend fun joinTournament(tournamentId: String): Result<Unit> =
        request { api.joinTournament(it, TournamentIdRequestDto(tournamentId)) }

    override suspend fun leaveTournament(tournamentId: String): Result<Unit> =
        request { api.leaveTournament(it, TournamentIdRequestDto(tournamentId)) }

    override suspend fun cancelTournament(tournamentId: String): Result<Unit> =
        request { api.cancelTournament(it, tournamentId) }

    private suspend fun <T> request(call: suspend (authorization: String) -> T): Result<T> {
        return try {
            val result = session.authorized(call)
            if (result == null) {
                // The app goes back to Login on its own once the user is signed out
                Result.failure(IllegalStateException("You are signed out."))
            } else {
                Result.success(result)
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
