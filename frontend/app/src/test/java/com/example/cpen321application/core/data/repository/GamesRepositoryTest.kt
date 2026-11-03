package com.example.cpen321application.core.data.repository

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.GameTournament
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.toUserMessage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
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
 * GamesRepository and CourtsRepository against a fake backend (MockWebServer), so no emulator is needed.
 * The JSON is shaped like the backend's responses.
 */
class GamesRepositoryTest {
    private val gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b"
    private val signedIn = User("alice", "access", "refresh")

    // A game as GET /games/feed returns it: court, players, rounds and forecast
    private val feedGameJson = """
        {
          "id": "$gameId",
          "format": "DOUBLES",
          "status": "OPEN",
          "startsAt": "2026-11-02T18:00:00.000Z",
          "courtId": 3,
          "createdAt": "2026-11-01T10:00:00.000Z",
          "gameSource": "SCHEDULED",
          "court": { "id": 3, "name": "Kitsilano Beach", "latitude": 49.27, "longitude": -123.15 },
          "players": [
            { "id": "u1", "username": "alice", "name": "Alice A", "rating": 1512.5, "team": 1, "acceptedAt": "2026-11-01T10:00:00.000Z" },
            { "id": "u2", "username": "bob", "name": "Bob B", "rating": 1490, "team": 2, "acceptedAt": "2026-11-01T11:00:00.000Z" }
          ],
          "rounds": [],
          "forecast": { "at": "2026-11-02T18:00:00.000Z", "temperature": 11.4, "description": "light rain", "icon": "10d", "precipitationChance": 0.4 }
        }
    """.trimIndent()

    // The same game as GET /games returns it: no forecast, and rounds once the score is in
    private val completedGameJson = """
        {
          "id": "$gameId",
          "format": "SINGLES",
          "status": "COMPLETED",
          "startsAt": "2026-11-02T18:00:00.000Z",
          "courtId": 3,
          "createdAt": "2026-11-01T10:00:00.000Z",
          "gameSource": "MATCHED",
          "court": { "id": 3, "name": "Kitsilano Beach", "latitude": 49.27, "longitude": -123.15 },
          "players": [],
          "rounds": [
            { "number": 1, "team1Score": 6, "team2Score": 4 },
            { "number": 2, "team1Score": 3, "team2Score": 6 }
          ]
        }
    """.trimIndent()

    private val tournamentGameJson = """
        {
          "id": "$gameId",
          "format": "SINGLES",
          "status": "BOOKED",
          "startsAt": "2026-11-02T19:00:00.000Z",
          "courtId": 3,
          "createdAt": "2026-11-02T18:30:00.000Z",
          "gameSource": "TOURNAMENT",
          "court": { "id": 3, "name": "Kitsilano Beach", "latitude": 49.27, "longitude": -123.15 },
          "players": [
            { "id": "u1", "username": "alice", "name": "Alice A", "rating": 1512.5, "team": 1, "acceptedAt": "2026-11-02T18:30:00.000Z" },
            { "id": "u2", "username": "bob", "name": "Bob B", "rating": 1490, "team": 2, "acceptedAt": "2026-11-02T18:30:00.000Z" }
          ],
          "rounds": [],
          "tournament": { "id": "t1", "name": "Sunday Smash", "round": 2 }
        }
    """.trimIndent()  // A tournament semifinal as GET /games/me returns it: TOURNAMENT source and the tournament's label

    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    // Created on first use, after the server started
    private val api by lazy {
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun session(user: User? = signedIn) = AuthSession(api, user) {}

    private fun repository(user: User? = signedIn) = GamesRepositoryImpl(session(user), api)

    private fun respond(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS)!!

    private fun RecordedRequest.jsonBody() = JSONObject(body.readUtf8())

    // Input: GET /games/feed returns one game with players and a forecast
    // Expected behavior: GET with the access token as a Bearer token
    // Expected output: the game with its court, players, start time and forecast parsed
    @Test
    fun getFeedParsesGamesWithTheirForecast() = runBlocking {
        respond(200, """{ "games": [$feedGameJson] }""")

        val games = repository().getFeed().getOrThrow()

        val request = takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/games/feed", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))

        val game = games.single()
        assertEquals(gameId, game.id)
        assertEquals(Format.DOUBLES, game.format)
        assertEquals(GameStatus.OPEN, game.status)
        assertEquals(GameSource.SCHEDULED, game.source)
        assertEquals(Instant.parse("2026-11-02T18:00:00Z"), game.startsAt)
        assertEquals(Court(3, "Kitsilano Beach", 49.27, -123.15), game.court)
        assertEquals(
            listOf(
                Player("u1", "alice", "Alice A", 1512.5, 1, acceptedAt = Instant.parse("2026-11-01T10:00:00Z")),
                Player("u2", "bob", "Bob B", 1490.0, 2, acceptedAt = Instant.parse("2026-11-01T11:00:00Z"))
            ),
            game.players
        )
        assertEquals(Forecast(11.4, "light rain", 0.4), game.forecast)
    }

    // Input: GET /games/me returns games without a forecast
    // Expected output: the games, with a null forecast
    @Test
    fun getMyGamesParsesGamesWithoutForecast() = runBlocking {
        respond(200, """{ "games": [$completedGameJson] }""")

        val games = repository().getMyGames().getOrThrow()

        assertEquals("/games/me", takeRequest().path)
        assertNull(games.single().forecast)
    }

    // Input: GET /games/me returns a tournament semifinal next to a game with "tournament": null
    // Expected output: the TOURNAMENT source and the tournament label are parsed, not a crash;
    // the regular game has no tournament label
    @Test
    fun getMyGamesParsesTournamentGames() = runBlocking {
        val regularGameJson = completedGameJson.replace("\"rounds\": [", "\"tournament\": null, \"rounds\": [")
        respond(200, """{ "games": [$tournamentGameJson, $regularGameJson] }""")

        val (tournamentGame, regularGame) = repository().getMyGames().getOrThrow()

        assertEquals(GameSource.TOURNAMENT, tournamentGame.source)
        assertEquals(GameStatus.BOOKED, tournamentGame.status)
        assertEquals(GameTournament("t1", "Sunday Smash", 2), tournamentGame.tournament)
        assertEquals(GameSource.MATCHED, regularGame.source)
        assertNull(regularGame.tournament)
    }

    // Input: GET /games?gameId=... returns a completed game
    // Expected behavior: the game ID is sent as a query parameter
    // Expected output: the game with its rounds in order
    @Test
    fun getGameSendsTheGameIdAndParsesRounds() = runBlocking {
        respond(200, """{ "game": $completedGameJson }""")

        val game = repository().getGame(gameId).getOrThrow()

        assertEquals("/games?gameId=$gameId", takeRequest().path)
        assertEquals(GameStatus.COMPLETED, game.status)
        assertEquals(GameSource.MATCHED, game.source)
        assertEquals(listOf(Round(1, 6, 4), Round(2, 3, 6)), game.rounds)
    }

    // Input: user schedules a doubles game
    // Expected behavior: POST /games with format, ISO start time with a time zone, court and SCHEDULED source
    // Expected output: the new game's ID
    @Test
    fun createGameSendsAScheduledGame() = runBlocking {
        respond(201, """{ "game": { "id": "$gameId", "format": "DOUBLES", "status": "OPEN" } }""")

        val newGameId = repository()
            .createGame(Format.DOUBLES, Instant.parse("2026-11-02T18:00:00Z"), 3)
            .getOrThrow()

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/games", request.path)
        val body = request.jsonBody()
        assertEquals("DOUBLES", body.getString("format"))
        assertEquals("2026-11-02T18:00:00Z", body.getString("startsAt"))
        assertEquals(3, body.getInt("courtId"))
        assertEquals("SCHEDULED", body.getString("gameSource"))
        assertEquals(gameId, newGameId)
    }

    // Input: DELETE /games answers 204 No Content
    // Expected behavior: DELETE with the game ID as a query parameter
    // Expected output: success
    @Test
    fun cancelGameDeletesTheGame() = runBlocking {
        respond(204)

        val result = repository().cancelGame(gameId)

        val request = takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/games?gameId=$gameId", request.path)
        assertTrue(result.isSuccess)
    }

    // Input: POST /games/join answers 204 No Content
    // Expected behavior: the game ID and team are sent in the body
    // Expected output: success
    @Test
    fun joinGameSendsTheTeam() = runBlocking {
        respond(204)

        val result = repository().joinGame(gameId, 2)

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/games/join", request.path)
        val body = request.jsonBody()
        assertEquals(gameId, body.getString("gameId"))
        assertEquals(2, body.getInt("team"))
        assertTrue(result.isSuccess)
    }

    // Input: GET /games returns a matched game where one player hasn't accepted yet
    // Expected output: that player's acceptedAt is null
    @Test
    fun getGameParsesAPlayerWhoHasNotAccepted() = runBlocking {
        val matchedGameJson = completedGameJson.replace(
            "\"players\": []",
            "\"players\": [{ \"id\": \"u2\", \"username\": \"bob\", \"name\": \"Bob B\", \"rating\": 1490, \"team\": 2, \"acceptedAt\": null }]"
        )
        respond(200, """{ "game": $matchedGameJson }""")

        val game = repository().getGame(gameId).getOrThrow()

        assertNull(game.players.single().acceptedAt)
    }

    // Input: POST /games/accept answers 204 No Content
    // Expected behavior: POST with the game ID in the body and the access token
    // Expected output: success
    @Test
    fun acceptMatchSendsTheGameId() = runBlocking {
        respond(204)

        val result = repository().acceptMatch(gameId)

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/games/accept", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals(gameId, request.jsonBody().getString("gameId"))
        assertTrue(result.isSuccess)
    }

    // Input: POST /games/invite answers 200 { notified: true }, then 200 { notified: false }
    // Expected behavior: POST with the game ID and the friend's user ID
    // Expected output: whether the friend's device was notified
    @Test
    fun inviteFriendSendsTheGameAndFriend() = runBlocking {
        respond(200, """{ "notified": true }""")
        respond(200, """{ "notified": false }""")

        val notified = repository().inviteFriend(gameId, "u2")
        val notNotified = repository().inviteFriend(gameId, "u3")

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/games/invite", request.path)
        val body = request.jsonBody()
        assertEquals(gameId, body.getString("gameId"))
        assertEquals("u2", body.getString("userId"))
        assertEquals(true, notified.getOrNull())
        assertEquals(false, notNotified.getOrNull())
    }

    // Input: POST /games/leave answers 204 No Content
    // Expected output: success, with only the game ID in the body
    @Test
    fun leaveGameSendsTheGameId() = runBlocking {
        respond(204)

        val result = repository().leaveGame(gameId)

        val request = takeRequest()
        assertEquals("/games/leave", request.path)
        assertEquals(gameId, request.jsonBody().getString("gameId"))
        assertTrue(result.isSuccess)
    }

    // Input: PATCH /games/time answers 200 with the updated game
    // Expected behavior: PATCH with the new start time as ISO with a time zone
    // Expected output: success (the screen reloads the game itself)
    @Test
    fun changeGameTimeSendsTheNewStartTime() = runBlocking {
        respond(200, """{ "game": { "id": "$gameId" } }""")

        val result = repository().changeGameTime(gameId, Instant.parse("2026-11-03T09:30:00Z"))

        val request = takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/games/time", request.path)
        assertEquals("2026-11-03T09:30:00Z", request.jsonBody().getString("startsAt"))
        assertTrue(result.isSuccess)
    }

    // Input: PATCH /games/location answers 200 with the updated game
    // Expected output: success, with the new court ID in the body
    @Test
    fun changeGameLocationSendsTheNewCourt() = runBlocking {
        respond(200, """{ "game": { "id": "$gameId" } }""")

        val result = repository().changeGameLocation(gameId, 5)

        val request = takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/games/location", request.path)
        assertEquals(5, request.jsonBody().getInt("courtId"))
        assertTrue(result.isSuccess)
    }

    // Input: user reports a three-set score
    // Expected behavior: POST /games/score with rounds as [team 1, team 2] pairs, in order
    // Expected output: success
    @Test
    fun reportScoreSendsRoundsAsPairs() = runBlocking {
        respond(200, """{ "game": { "id": "$gameId" }, "rounds": [] }""")

        val result = repository().reportScore(gameId, listOf(Pair(6, 4), Pair(3, 6), Pair(7, 5)))

        val request = takeRequest()
        assertEquals("/games/score", request.path)
        val rounds: JSONArray = request.jsonBody().getJSONArray("rounds")
        assertEquals("[[6,4],[3,6],[7,5]]", rounds.toString())
        assertTrue(result.isSuccess)
    }

    // Input: POST /games/join answers 409 { "error": "Team is full" }
    // Expected output: a failure whose message for the user is the backend's error
    @Test
    fun returnsTheBackendErrorOnFailure() = runBlocking {
        respond(409, """{ "error": "Team is full" }""")

        val error = repository().joinGame(gameId, 1).exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(409, (error as HttpException).code())
        assertEquals("Team is full", error.toUserMessage())
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

        val error = repository.getMyGames().exceptionOrNull()

        assertTrue(error is IOException)
    }

    // Input: GET /courts returns two courts
    // Expected output: both courts, in the backend's order
    @Test
    fun getCourtsParsesCourts() = runBlocking {
        respond(
            200,
            """
            { "courts": [
              { "id": 3, "name": "Kitsilano Beach", "latitude": 49.27, "longitude": -123.15 },
              { "id": 1, "name": "Stanley Park", "latitude": 49.3, "longitude": -123.14 }
            ] }
            """.trimIndent()
        )

        val courts = CourtsRepositoryImpl(session(), api).getCourts().getOrThrow()

        assertEquals("/courts", takeRequest().path)
        assertEquals(listOf(3, 1), courts.map { it.id })
        assertEquals("Stanley Park", courts[1].name)
    }
}
