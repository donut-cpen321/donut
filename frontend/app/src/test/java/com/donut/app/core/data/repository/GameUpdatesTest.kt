package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.GamesApi
import com.donut.app.core.network.ServerSentEvent
import com.donut.app.core.network.ServerSentEventParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The GET /games/events stream, against a fake backend (MockWebServer) that sends events the way the backend does
 */
class GameUpdatesTest {
    private val gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b"
    private val signedIn = User("alice", "access", "refresh")
    private val server = MockWebServer()

    private fun gameJson(status: String, playerIds: List<String>): String {
        val players = playerIds.mapIndexed { index, id ->
            """{ "id": "$id", "username": "$id", "name": "$id", "rating": 1500, "team": ${index + 1} }"""
        }
        return """{ "game": { "id": "$gameId", "format": "SINGLES", "status": "$status",
            "startsAt": "2026-11-02T18:00:00.000Z", "courtId": 3, "createdAt": "2026-11-01T10:00:00.000Z",
            "gameSource": "SCHEDULED",
            "court": { "id": 3, "name": "Kitsilano Beach", "latitude": 49.27, "longitude": -123.15 },
            "players": [${players.joinToString()}], "rounds": [], "forecast": null } }"""
            .replace("\n", "")
    }

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun repository(): GamesRepositoryImpl {
        val session = AuthSession(retrofit.create(ApiService::class.java), signedIn) {}
        val events = EventStreamApi(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), server.url("/"))
        return GamesRepositoryImpl(session, retrofit.create(GamesApi::class.java), events)
    }

    private fun stream(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
    }

    // Input: lines of an event stream with a heartbeat comment and a two-line data field
    // Expected behavior: comments are skipped, data lines are joined, and the event name resets after each event
    // Expected output: two events, the second named "message"
    @Test
    fun parser_skipsCommentsAndJoinsDataLines() {
        val parser = ServerSentEventParser()
        val lines = listOf(": ping", "", "event: gameUpdated", "data: {\"a\":", "data: 1}", "", "data: x", "")

        val events = lines.mapNotNull(parser::feed)

        assertEquals(
            listOf(ServerSentEvent("gameUpdated", "{\"a\":\n1}"), ServerSentEvent("message", "x")),
            events
        )
    }

    // Input: the backend sends the current game, a heartbeat, the game after a second player joined,
    // then gameDeleted
    // Expected behavior: the request carries the access token; the flow ends after Deleted
    // Expected output: Changed (1 player), Changed (2 players, BOOKED), Deleted
    @Test
    fun gameUpdates_emitsEachPushedGameUntilDeleted() = runBlocking {
        stream(
            "event: gameUpdated\ndata: ${gameJson("OPEN", listOf("u1"))}\n\n" +
                ": ping\n\n" +
                "event: gameUpdated\ndata: ${gameJson("BOOKED", listOf("u1", "u2"))}\n\n" +
                "event: gameDeleted\ndata: {\"gameId\":\"$gameId\"}\n\n"
        )

        val updates = withTimeout(5_000) { repository().gameUpdates(gameId).toList() }

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/games/events?gameId=$gameId", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals(3, updates.size)
        val first = updates[0] as GameUpdate.Changed
        val second = updates[1] as GameUpdate.Changed
        assertEquals(listOf("u1"), first.game.players.map { it.id })
        assertEquals(GameStatus.BOOKED, second.game.status)
        assertEquals(listOf("u1", "u2"), second.game.players.map { it.id })
        assertEquals(GameUpdate.Deleted, updates[2])
    }

    // Input: the stream is refused with 401 because the access token expired
    // Expected behavior: the token is refreshed and the stream opened again with the new one
    // Expected output: the game from the second stream
    @Test
    fun gameUpdates_refreshesAnExpiredTokenAndReconnects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{ "code": "INVALID_AUTH_TOKEN" }"""))
        server.enqueue(MockResponse().setBody("""{ "accessToken": "access2", "refreshToken": "refresh2" }"""))
        stream("event: gameUpdated\ndata: ${gameJson("OPEN", listOf("u1"))}\n\n")

        val update = withTimeout(5_000) { repository().gameUpdates(gameId).first() }

        assertTrue(update is GameUpdate.Changed)
        server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("/user/refresh", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertEquals("Bearer access2", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Authorization"))
    }

    // Input: the backend sends the game, then ends the stream (restarting, or closing a user's extra streams)
    // Expected behavior: the game is delivered, then the end fails like a dropped connection, so the screen
    // reconnects instead of silently stopping
    // Expected output: one Changed, then IOException
    @Test
    fun gameUpdates_failsLikeADroppedConnectionWhenTheBackendEndsTheStream() = runBlocking {
        stream("event: gameUpdated\ndata: ${gameJson("OPEN", listOf("u1"))}\n\n")
        val received = mutableListOf<GameUpdate>()

        val error = runCatching {
            withTimeout(5_000) { repository().gameUpdates(gameId).collect { received.add(it) } }
        }.exceptionOrNull()

        assertEquals(1, received.size)
        assertTrue(error is IOException)
    }

    // Input: a response that is neither the stream nor an error: a redirect with nowhere to go
    // Expected behavior: it fails like a dropped connection, so the screen reconnects, instead of hanging
    // Expected output: IOException
    @Test
    fun gameUpdates_failsLikeADroppedConnectionOnAnUnexpectedResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302))

        val error = runCatching { withTimeout(5_000) { repository().gameUpdates(gameId).toList() } }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    // Input: the stream is refused with 404 because the game does not exist
    // Expected behavior: the flow fails with the backend's error
    // Expected output: HttpException 404
    @Test
    fun gameUpdates_failsWithTheBackendsError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "code": "GAME_NOT_FOUND" }"""))

        val error = runCatching { withTimeout(5_000) { repository().gameUpdates(gameId).toList() } }.exceptionOrNull()

        assertEquals(404, (error as HttpException).code())
    }
}
