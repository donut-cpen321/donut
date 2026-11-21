package com.donut.app.feature.matchmaking

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.EventStreamApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * MatchmakingRepositoryImpl against a fake backend (MockWebServer), so no emulator is needed.
 */
class MatchmakingRepositoryTest {
    private val ubc = MatchLocation("UBC Tennis Centre", 49.2596, -123.2432)
    private val queueEntryJson =
        """{"request":{"format":"DOUBLES","latitude":49.2596,"longitude":-123.2432,"createdAt":"2026-11-01T12:00:00.000Z"}}"""

    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun repository(user: User? = User("alice", "access", "refresh")): MatchmakingRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        val events = EventStreamApi(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), server.url("/"))
        return MatchmakingRepositoryImpl(AuthSession(api, user) {}, api, events)
    }

    private fun respond(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // Input: join the queue for doubles at UBC; backend answers 201
    // Expected behavior: POST /matchmaking with the token, format and coordinates
    // Expected output: success
    @Test
    fun joinQueueSendsFormatAndLocation() = runBlocking {
        respond(201, queueEntryJson)

        val result = repository().joinQueue(MatchFormat.DOUBLES, ubc)

        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertTrue(result.isSuccess)
        assertEquals("POST", request.method)
        assertEquals("/matchmaking", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals("DOUBLES", body.getString("format"))
        assertEquals(49.2596, body.getDouble("latitude"), 0.0)
        assertEquals(-123.2432, body.getDouble("longitude"), 0.0)
    }

    // Input: join while a matched game is coming up; backend answers 403
    // Expected output: failure with ActiveMatchException, so the screen can say why
    @Test
    fun joinQueueReportsAnActiveMatch() = runBlocking {
        respond(403, """{"error":"Already has a match"}""")

        val result = repository().joinQueue(MatchFormat.SINGLES, ubc)

        assertTrue(result.exceptionOrNull() is ActiveMatchException)
    }

    // Input: join while already in the queue; backend answers 409
    // Expected behavior: the player is waiting either way
    // Expected output: success
    @Test
    fun joinQueueWhenAlreadyQueuedSucceeds() = runBlocking {
        respond(409, """{"error":"Already in the queue"}""")

        assertTrue(repository().joinQueue(MatchFormat.SINGLES, ubc).isSuccess)
    }

    // Input: join; backend answers 500
    // Expected output: failure
    @Test
    fun joinQueueServerErrorFails() = runBlocking {
        respond(500, """{"error":"Internal server error"}""")

        assertTrue(repository().joinQueue(MatchFormat.SINGLES, ubc).isFailure)
    }

    // Input: join with nobody signed in
    // Expected behavior: no request is sent
    // Expected output: failure
    @Test
    fun joinQueueSignedOutFails() = runBlocking {
        assertTrue(repository(user = null).joinQueue(MatchFormat.SINGLES, ubc).isFailure)
        assertEquals(0, server.requestCount)
    }

    // Input: leave the queue; backend answers 204 with no body
    // Expected behavior: DELETE /matchmaking with the token
    // Expected output: success
    @Test
    fun leaveQueueSendsDelete() = runBlocking {
        respond(204)

        val result = repository().leaveQueue()

        val request = server.takeRequest()
        assertTrue(result.isSuccess)
        assertEquals("DELETE", request.method)
        assertEquals("/matchmaking", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
    }

    // Input: leave; backend answers 500
    // Expected output: failure
    @Test
    fun leaveQueueServerErrorFails() = runBlocking {
        respond(500)

        assertTrue(repository().leaveQueue().isFailure)
    }

    // Input: check the queue while waiting for doubles; backend answers 200
    // Expected output: the format the player is waiting for
    @Test
    fun queuedFormatReturnsTheFormat() = runBlocking {
        respond(200, queueEntryJson)

        assertEquals(MatchFormat.DOUBLES, repository().queuedFormat().getOrThrow())
        assertEquals("GET", server.takeRequest().method)
    }

    // Input: check the queue after being matched (or never joining); backend answers 404
    // Expected output: success with null, the player is not waiting
    @Test
    fun queuedFormatNotInQueueIsNull() = runBlocking {
        respond(404, """{"error":"Not in the queue"}""")

        assertNull(repository().queuedFormat().getOrThrow())
    }

    // Input: check the queue; backend answers 500
    // Expected output: failure, so a server problem is not mistaken for a match
    @Test
    fun queuedFormatServerErrorFails() = runBlocking {
        respond(500)

        assertTrue(repository().queuedFormat().isFailure)
    }

    // Input: the backend's queue stream sends the players nearby, then the player is matched out of the queue
    // Expected behavior: GET /matchmaking/events with the token, held open rather than polled
    // Expected output: Nearby with the center and the other players' points, then NotQueued
    @Test
    fun queueUpdatesEmitsNearbyThenNotQueued() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: nearby\n" +
                    "data: {\"center\":{\"latitude\":49.2596,\"longitude\":-123.2432}," +
                    "\"players\":[{\"latitude\":49.268,\"longitude\":-123.168}]}\n\n" +
                    ": ping\n\n" +
                    "event: notQueued\ndata: {}\n\n"
            )
        )

        val updates = withTimeout(5_000) { repository().queueUpdates().take(2).toList() }

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/matchmaking/events", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals(
            listOf(
                QueueUpdate.Nearby(NearbyPlayers(MapPoint(49.2596, -123.2432), listOf(MapPoint(49.268, -123.168)))),
                QueueUpdate.NotQueued
            ),
            updates
        )
    }

    // Input: signed out
    // Expected behavior: no stream is opened
    // Expected output: the flow ends right away with nothing
    @Test
    fun queueUpdatesSignedOutEndsRightAway() = runBlocking {
        val updates = withTimeout(5_000) { repository(user = null).queueUpdates().toList() }

        assertTrue(updates.isEmpty())
        assertEquals(0, server.requestCount)
    }
}
