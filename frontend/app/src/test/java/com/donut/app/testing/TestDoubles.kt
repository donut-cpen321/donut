package com.donut.app.testing

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.repository.EquipmentRepository
import com.donut.app.core.data.repository.GameUpdate
import com.donut.app.core.data.repository.GamesRepository
import com.donut.app.core.data.repository.TournamentsRepository
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.dto.CreateEquipmentRequestDto
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.UpdateEquipmentRequestDto
import kotlinx.coroutines.flow.Flow
import java.lang.reflect.Proxy
import java.time.Instant

// Test doubles shared by the view model tests: subclass the Unused repositories and override only what a test
// uses; anything else it calls fails the test

/**
 * A GamesRepository whose every route fails the test. Override the routes a test uses
 */
open class UnusedGamesRepository : GamesRepository {
    override suspend fun getFeed(): Result<List<Game>> = error("unused")
    override suspend fun getMyGames(): Result<List<Game>> = error("unused")
    override suspend fun getGame(gameId: String): Result<Game> = error("unused")
    override suspend fun createGame(format: Format, startsAt: Instant, courtId: Int): Result<String> = error("unused")
    override suspend fun cancelGame(gameId: String): Result<Unit> = error("unused")
    override suspend fun joinGame(gameId: String, team: Int): Result<Unit> = error("unused")
    override suspend fun leaveGame(gameId: String): Result<Unit> = error("unused")
    override suspend fun acceptMatch(gameId: String): Result<Unit> = error("unused")
    override suspend fun inviteFriend(gameId: String, userId: String): Result<Boolean> = error("unused")
    override suspend fun startGame(gameId: String): Result<Unit> = error("unused")
    override suspend fun changeGameTime(gameId: String, startsAt: Instant): Result<Unit> = error("unused")
    override suspend fun changeGameLocation(gameId: String, courtId: Int): Result<Unit> = error("unused")
    override suspend fun setGameEquipment(gameId: String, equipmentIds: List<Int>): Result<Unit> = error("unused")
    override suspend fun reportScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit> = error("unused")
    override suspend fun confirmScore(gameId: String, rounds: List<Pair<Int, Int>>): Result<Unit> = error("unused")
    override fun gameUpdates(gameId: String): Flow<GameUpdate> = error("unused")
}

/**
 * A TournamentsRepository whose every route fails the test. Override the routes a test uses
 */
open class UnusedTournamentsRepository : TournamentsRepository {
    override suspend fun getFeed(): Result<List<Tournament>> = error("unused")
    override suspend fun getMyTournaments(): Result<List<Tournament>> = error("unused")
    override suspend fun getTournament(tournamentId: String): Result<Tournament> = error("unused")
    override suspend fun createTournament(
        name: String,
        format: Format,
        startsAt: Instant,
        courtId: Int
    ): Result<String> = error("unused")
    override suspend fun joinTournament(tournamentId: String): Result<Unit> = error("unused")
    override suspend fun leaveTournament(tournamentId: String): Result<Unit> = error("unused")
    override suspend fun cancelTournament(tournamentId: String): Result<Unit> = error("unused")
    override fun tournamentUpdates(tournamentId: String): Flow<Tournament> = error("unused")
}

open class UnusedEquipmentRepository : EquipmentRepository {
    override suspend fun getEquipment(session: AuthSession): Result<List<EquipmentDto>> = Result.success(emptyList())
    override suspend fun createEquipment(
        session: AuthSession,
        request: CreateEquipmentRequestDto
    ): Result<EquipmentDto> = error("unused")
    override suspend fun updateEquipment(
        session: AuthSession,
        id: Int,
        request: UpdateEquipmentRequestDto
    ): Result<EquipmentDto> = error("unused")
    override suspend fun deleteEquipment(session: AuthSession, id: Int): Result<Unit> = error("unused")
}

/**
 * A session signed in as a user, for code that only needs to know who is signed in: its API fails the test if
 * called, e.g. to refresh the token
 * @param username the signed-in user
 */
fun signedInSession(username: String = "alice"): AuthSession {
    val apiType = ApiService::class.java
    val unusedApi = Proxy.newProxyInstance(apiType.classLoader, arrayOf(apiType)) { _, _, _ ->
        error("unused")
    } as ApiService
    return AuthSession(unusedApi, User(username, "access", "refresh")) {}
}
