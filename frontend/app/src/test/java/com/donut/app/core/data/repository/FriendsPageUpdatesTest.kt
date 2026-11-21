package com.donut.app.core.data.repository

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The GET /friends/events stream, against a fake backend (MockWebServer) that sends events the way the backend does
 */
class FriendsPageUpdatesTest {
    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun updates(): FriendsPageUpdatesImpl {
        val events = EventStreamApi(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), server.url("/"))
        return FriendsPageUpdatesImpl(events)
    }

    private fun session(): AuthSession {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        return AuthSession(api, User("alice", "access", "refresh")) {}
    }

    // Input: the backend sends the friends list (one friend playing), the requests, an unknown event, then the
    // friends list again once the friend stopped playing
    // Expected behavior: the request carries the access token; the unknown event is skipped
    // Expected output: Friends (playing), Requests (one incoming), Friends (not playing)
    @Test
    fun updates_readsFriendsAndRequestsEvents() = runBlocking {
        val playing = """{ "gameId": "game-1", "format": "SINGLES", "startedAt": "2026-11-19T20:00:00.000Z",
            "courtName": "Kitsilano Beach Park" }""".replace("\n", "")
        val carol = """{ "id": "u3", "username": "carol" }"""
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: friends\ndata: {\"friends\":[{\"id\":\"u2\",\"username\":\"bob\",\"playing\":$playing}]}\n\n" +
                    "event: requests\ndata: {\"incoming\":[$carol],\"outgoing\":[]}\n\n" +
                    "event: somethingNew\ndata: {}\n\n" +
                    "event: friends\ndata: {\"friends\":[{\"id\":\"u2\",\"username\":\"bob\",\"playing\":null}]}\n\n"
            )
        )

        val received = withTimeout(5_000) { updates().updates(session()).take(3).toList() }

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/friends/events", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))

        val first = received[0] as FriendsPageUpdate.Friends
        assertEquals("u2", first.friends.single().userId)
        assertEquals("Kitsilano Beach Park", first.friends.single().playing?.courtName)
        val requests = received[1] as FriendsPageUpdate.Requests
        assertEquals(listOf("u3"), requests.incoming.map { it.userId })
        val last = received[2] as FriendsPageUpdate.Friends
        assertEquals(null, last.friends.single().playing)
    }
}
