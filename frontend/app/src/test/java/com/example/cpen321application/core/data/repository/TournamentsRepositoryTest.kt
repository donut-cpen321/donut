package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.data.model.TournamentCreator
import com.example.cpen321application.core.data.model.TournamentPlayer
import com.example.cpen321application.core.data.model.TournamentStatus
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.TournamentApi
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * TournamentsRepository against a fake backend (MockWebServer), so no emulator is needed.
 * The JSON is shaped like the backend's responses in the frozen API contract.
 */
class TournamentsRepositoryTest {
    private val tournamentId = "0b7e1f4a-2c3d-4e5f-8a9b-0c1d2e3f4a5b"
    private val gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b"
    private val signedIn = User("alice@example.com", "access", "refresh")

    private val courtJson = """{ "id": 3, "name": "Kitsilano Beach Park", "latitude": 49.27, "longitude": -123.15 }"""
    private val creatorJson = """{ "id": "u1", "username": "alice@example.com", "name": "Alice A", "rating": 1512.5 }"""

    private val feedTournamentJson = """
        {
          "id": "$tournamentId",
          "name": "Sunday Smash",
          "format": "SINGLES",
          "status": "REGISTRATION",
          "startsAt": "2026-11-08T17:00:00.000Z",
          "courtId": 3,
          "creatorId": "u1",
          "winningTeam": null,
          "createdAt": "2026-11-02T17:00:00.000Z",
          "capacity": 8,
          "court": $courtJson,
          "creator": $creatorJson,
          "players": [
            { "id": "u1", "username": "alice@example.com", "name": "Alice A", "rating": 1512.5, "team": null, "joinedAt": "2026-11-02T17:00:00.000Z" },
            { "id": "u2", "username": "bob@example.com", "name": "Bob B", "rating": 1490, "team": null, "joinedAt": "2026-11-02T18:00:00.000Z" }
          ],
          "forecast": { "at": "2026-11-08T17:00:00.000Z", "temperature": 11.4, "description": "light rain", "icon": "10d", "precipitationChance": 0.4 }
        }
    """.trimIndent()  // A tournament as GET /tournaments/feed returns it: open, two players, no draw yet, with a forecast

    private val myTournamentJson = """
        {
          "id": "$tournamentId",
          "name": "Doubles Day",
          "format": "DOUBLES",
          "status": "IN_PROGRESS",
          "startsAt": "2026-11-02T17:00:00.000Z",
          "courtId": 3,
          "creatorId": "u1",
          "winningTeam": null,
          "createdAt": "2026-11-01T17:00:00.000Z",
          "capacity": 16,
          "court": $courtJson,
          "creator": $creatorJson,
          "players": []
        }
    """.trimIndent()  // A tournament as GET /tournaments/me returns it: no forecast and no matches keys at all

    private val detailsJson = """
        {
          "id": "$tournamentId",
          "name": "Sunday Smash",
          "format": "SINGLES",
          "status": "COMPLETED",
          "startsAt": "2026-11-02T17:00:00.000Z",
          "courtId": 3,
          "creatorId": "u1",
          "winningTeam": 1,
          "createdAt": "2026-11-01T17:00:00.000Z",
          "capacity": 8,
          "court": $courtJson,
          "creator": $creatorJson,
          "players": [
            { "id": "u1", "username": "alice@example.com", "name": "Alice A", "rating": 1512.5, "team": 1, "joinedAt": "2026-11-01T17:00:00.000Z" }
          ],
          "matches": [
            { "round": 1, "position": 0, "team1": 1, "team2": 8, "winner": 1,
              "game": { "id": "$gameId", "status": "COMPLETED", "startsAt": "2026-11-02T17:00:00.000Z",
                        "court": $courtJson,
                        "rounds": [ { "number": 1, "team1Score": 6, "team2Score": 4 }, { "number": 2, "team1Score": 7, "team2Score": 5 } ] } },
            { "round": 2, "position": 0, "team1": 1, "team2": null, "winner": null, "game": null }
          ]
        }
    """.trimIndent()  // A tournament as GET /tournaments?tournamentId= returns it: drawn, one quarterfinal played, one semifinal waiting

    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private val retrofit by lazy {  // Created on first use, after the server started
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun repository(user: User? = signedIn): TournamentsRepository {
        val session = AuthSession(retrofit.create(ApiService::class.java), user) {}
        return TournamentsRepositoryImpl(session, retrofit.create(TournamentApi::class.java))
    }

    private fun respond(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS)!!

    private fun RecordedRequest.jsonBody() = JSONObject(body.readUtf8())

    // Input: GET /tournaments/feed returns one open tournament with two players and a forecast
    // Expected behavior: GET with the access token as a Bearer token
    // Expected output: the tournament with its court, creator, players (no team yet), capacity and forecast parsed
    @Test
    fun getFeedParsesTournamentsWithTheirForecast() = runBlocking {
        respond(200, """{ "tournaments": [$feedTournamentJson] }""")

        val tournaments = repository().getFeed().getOrThrow()

        val request = takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/tournaments/feed", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))

        val tournament = tournaments.single()
        assertEquals(tournamentId, tournament.id)
        assertEquals("Sunday Smash", tournament.name)
        assertEquals(Format.SINGLES, tournament.format)
        assertEquals(TournamentStatus.REGISTRATION, tournament.status)
        assertEquals(Instant.parse("2026-11-08T17:00:00Z"), tournament.startsAt)
        assertEquals(Court(3, "Kitsilano Beach Park", 49.27, -123.15), tournament.court)
        assertEquals(TournamentCreator("u1", "alice@example.com", "Alice A", 1512.5), tournament.creator)
        assertEquals(8, tournament.capacity)
        assertEquals(
            listOf(
                TournamentPlayer("u1", "alice@example.com", "Alice A", 1512.5, null),
                TournamentPlayer("u2", "bob@example.com", "Bob B", 1490.0, null)
            ),
            tournament.players
        )
        assertEquals(emptyList<Any>(), tournament.matches)
        assertNull(tournament.winningTeam)
        assertEquals(Forecast(11.4, "light rain", 0.4), tournament.forecast)
    }

    // Input: GET /tournaments/me returns a tournament without the forecast and matches keys
    // Expected output: the tournament, with a null forecast and no matches instead of a crash
    @Test
    fun getMyTournamentsParsesTournamentsWithoutForecastOrMatches() = runBlocking {
        respond(200, """{ "tournaments": [$myTournamentJson] }""")

        val tournament = repository().getMyTournaments().getOrThrow().single()

        val request = takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/tournaments/me", request.path)
        assertEquals(Format.DOUBLES, tournament.format)
        assertEquals(TournamentStatus.IN_PROGRESS, tournament.status)
        assertEquals(16, tournament.capacity)
        assertNull(tournament.forecast)
        assertTrue(tournament.matches.isEmpty())
    }

    // Input: GET /tournaments?tournamentId=... returns a completed tournament with two matches
    // Expected behavior: the tournament ID is sent as a query parameter
    // Expected output: the matches in order, the played one with its game and set scores, the waiting one without
    @Test
    fun getTournamentSendsTheIdAndParsesTheBracket() = runBlocking {
        respond(200, """{ "tournament": $detailsJson }""")

        val tournament = repository().getTournament(tournamentId).getOrThrow()

        assertEquals("/tournaments?tournamentId=$tournamentId", takeRequest().path)
        assertEquals(TournamentStatus.COMPLETED, tournament.status)
        assertEquals(1, tournament.winningTeam)
        assertEquals(1, tournament.players.single().team)

        val (quarterfinal, semifinal) = tournament.matches
        assertEquals(1, quarterfinal.round)
        assertEquals(0, quarterfinal.position)
        assertEquals(1, quarterfinal.team1)
        assertEquals(8, quarterfinal.team2)
        assertEquals(1, quarterfinal.winner)
        val game = quarterfinal.game!!
        assertEquals(gameId, game.id)
        assertEquals(GameStatus.COMPLETED, game.status)
        assertEquals(Instant.parse("2026-11-02T17:00:00Z"), game.startsAt)
        assertEquals(3, game.court.id)
        assertEquals(listOf(Round(1, 6, 4), Round(2, 7, 5)), game.rounds)

        assertEquals(2, semifinal.round)
        assertEquals(1, semifinal.team1)
        assertNull(semifinal.team2)
        assertNull(semifinal.winner)
        assertNull(semifinal.game)
    }

    // Input: user creates a doubles tournament
    // Expected behavior: POST /tournaments with name, format, ISO start time with a time zone and court
    // Expected output: the new tournament's ID
    @Test
    fun createTournamentSendsTheForm() = runBlocking {
        respond(201, """{ "tournament": { "id": "$tournamentId", "name": "Sunday Smash", "status": "REGISTRATION" } }""")

        val newId = repository()
            .createTournament("Sunday Smash", Format.DOUBLES, Instant.parse("2026-11-08T17:00:00Z"), 3)
            .getOrThrow()

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tournaments", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        val body = request.jsonBody()
        assertEquals("Sunday Smash", body.getString("name"))
        assertEquals("DOUBLES", body.getString("format"))
        assertEquals("2026-11-08T17:00:00Z", body.getString("startsAt"))
        assertEquals(3, body.getInt("courtId"))
        assertEquals(tournamentId, newId)
    }

    // Input: POST /tournaments/join answers 204 No Content
    // Expected behavior: POST with only the tournament ID in the body
    // Expected output: success
    @Test
    fun joinTournamentSendsTheId() = runBlocking {
        respond(204)

        val result = repository().joinTournament(tournamentId)

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tournaments/join", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals(tournamentId, request.jsonBody().getString("tournamentId"))
        assertTrue(result.isSuccess)
    }

    // Input: POST /tournaments/leave answers 204 No Content
    // Expected behavior: POST with only the tournament ID in the body
    // Expected output: success
    @Test
    fun leaveTournamentSendsTheId() = runBlocking {
        respond(204)

        val result = repository().leaveTournament(tournamentId)

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tournaments/leave", request.path)
        assertEquals(tournamentId, request.jsonBody().getString("tournamentId"))
        assertTrue(result.isSuccess)
    }

    // Input: DELETE /tournaments answers 204 No Content
    // Expected behavior: DELETE with the tournament ID as a query parameter
    // Expected output: success
    @Test
    fun cancelTournamentDeletesIt() = runBlocking {
        respond(204)

        val result = repository().cancelTournament(tournamentId)

        val request = takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/tournaments?tournamentId=$tournamentId", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertTrue(result.isSuccess)
    }

    // Input: POST /tournaments/join answers 409 { "error": "Tournament is full" }
    // Expected output: a failure whose message for the user is the backend's error
    @Test
    fun returnsTheBackendErrorOnConflict() = runBlocking {
        respond(409, """{ "error": "Tournament is full" }""")

        val error = repository().joinTournament(tournamentId).exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(409, (error as HttpException).code())
        assertEquals("Tournament is full", error.toUserMessage())
    }

    // Input: DELETE /tournaments answers 403 because the user did not create it
    // Expected output: a failure whose message for the user is the backend's error
    @Test
    fun returnsTheBackendErrorWhenNotTheCreator() = runBlocking {
        respond(403, """{ "error": "Only the creator can cancel the tournament" }""")

        val error = repository().cancelTournament(tournamentId).exceptionOrNull()

        assertEquals(403, (error as HttpException).code())
        assertEquals("Only the creator can cancel the tournament", error.toUserMessage())
    }

    // Input: POST /tournaments answers 400 with the failed check in details
    // Expected output: a failure whose message has the error and the failed check
    @Test
    fun returnsTheValidationErrorOnBadRequest() = runBlocking {
        respond(
            400,
            """{ "error": "Invalid request body", "details": { "fieldErrors": { "name": ["Name is too long"] } } }"""
        )

        val error = repository()
            .createTournament("x", Format.SINGLES, Instant.parse("2026-11-08T17:00:00Z"), 3)
            .exceptionOrNull()

        assertEquals("Invalid request body: Name is too long", error?.toUserMessage())
    }

    // Input: GET /tournaments answers 404 { "error": "Tournament not found" }
    // Expected output: a failure with the 404 and the backend's error
    @Test
    fun returnsNotFoundForAMissingTournament() = runBlocking {
        respond(404, """{ "error": "Tournament not found" }""")

        val error = repository().getTournament(tournamentId).exceptionOrNull()

        assertEquals(404, (error as HttpException).code())
        assertEquals("Tournament not found", error.toUserMessage())
    }

    // Input: no signed-in user
    // Expected behavior: nothing is sent to the backend
    // Expected output: a failure
    @Test
    fun failsWithoutRequestWhenSignedOut() = runBlocking {
        val result = repository(user = null).getFeed()

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    // Input: the backend is unreachable
    // Expected output: a failure with an IOException, not a crash
    @Test
    fun failsWhenTheBackendIsUnreachable() = runBlocking {
        val repository = repository()  // Builds the client while the server is still up
        server.shutdown()

        val error = repository.getMyTournaments().exceptionOrNull()

        assertTrue(error is IOException)
    }
}
