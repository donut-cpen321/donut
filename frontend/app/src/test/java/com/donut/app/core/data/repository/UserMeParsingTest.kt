package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.FriendsApi
import com.donut.app.core.network.GamesApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * GET /user/me and GET /friends as the backend really sends them: ratings are decimals
 */
class UserMeParsingTest {
    private val server = MockWebServer()

    @Before
    fun startServer() {
        UserPhotoCache.clear()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun repository(): Pair<FriendsRepositoryImpl, AuthSession> {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(ApiService::class.java)
        val session = AuthSession(api, User("chance@example.com", "access", "refresh")) {}
        val repository = FriendsRepositoryImpl(
            api,
            retrofit.create(GamesApi::class.java),
            retrofit.create(FriendsApi::class.java)
        )
        return repository to session
    }

    // Input: the profile with a decimal rating that rounds up
    // Expected output: every field is read, including the name, and the rating is rounded (1584.6 → 1585),
    // like the leaderboard and game screens
    @Test
    fun profileKeepsTheNameAndRoundsTheRating() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{ "user": { "id": "u1", "username": "chance@example.com", "name": "Chance Test",
                "email": "chance@example.com", "createdAt": "2026-10-01T12:00:00.000Z", "rating": 1584.6 } }"""
            )
        )
        val (repository, session) = repository()

        val me = repository.getUserMe(session).getOrThrow()

        assertEquals("Chance Test", me.name)
        assertEquals("chance@example.com", me.username)
        assertEquals(1585, me.elo)
    }

    // Input: GET /friends with one friend whose decimal rating is under "rating"
    // Expected output: the friend's rating is read (it used to be looked for under "elo" and was always null)
    @Test
    fun friendRatingIsRead() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{ "friends": [ { "id": "u2", "username": "rally@example.com", "name": "Rally Partner",
                "rating": 1519.6, "photoUrl": "https://example.com/rally.jpg", "friendsSince": "2026-10-01T12:00:00.000Z", "playing": null } ] }"""
            )
        )
        val (repository, session) = repository()

        val friend = repository.getFriends(session).getOrThrow().single()

        assertEquals(1519.6, friend.rating!!, 0.0)
        assertEquals("Rally Partner", friend.displayName)
        assertEquals("https://example.com/rally.jpg", friend.avatarUrl)
    }

    // Input: GET /friends/requests with one incoming and one outgoing request, shaped like the backend's
    // Expected output: every field of both is read, including when the request was sent ("requestedAt")
    // and the other user's rating, which used to be left empty
    @Test
    fun friendRequestsReadEveryField() = runBlocking {
        val requests = """{
            "incoming": [ { "id": "u3", "username": "lob@example.com", "name": "Lob Friend", "rating": 1480.25,
                "requestedAt": "2026-11-19T20:39:51.000Z" } ],
            "outgoing": [ { "id": "u4", "username": "ace@example.com", "name": "Ace Player", "rating": 1612.5,
                "requestedAt": "2026-11-18T09:00:00.000Z" } ] }"""
        server.enqueue(MockResponse().setBody(requests))
        server.enqueue(MockResponse().setBody(requests))
        val (repository, session) = repository()

        val incoming = repository.getFriendRequests(session).getOrThrow().single()
        val outgoing = repository.getOutgoingFriendRequests(session).getOrThrow().single()

        assertEquals("u3", incoming.userId)
        assertEquals("Lob Friend", incoming.displayName)
        assertEquals("2026-11-19T20:39:51.000Z", incoming.requestedAt)
        assertEquals(1480.25, incoming.rating!!, 0.0)
        assertEquals("u4", outgoing.userId)
        assertEquals("2026-11-18T09:00:00.000Z", outgoing.requestedAt)
        assertEquals(1612.5, outgoing.rating!!, 0.0)
    }

    @Test
    fun getUserPhotoReturnsPhotoUrlFromEndpoint() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{ "photoUrl": "https://lh3.googleusercontent.com/avatar.jpg" }""")
        )
        val (repository, session) = repository()

        val photoUrl = repository.getUserPhoto(session, "u1").getOrThrow()

        assertEquals("https://lh3.googleusercontent.com/avatar.jpg", photoUrl)
        assertEquals("/user/u1/photo", server.takeRequest().path)
    }

    @Test
    fun getUserPhotoReturnsNullWhenPhotoNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val (repository, session) = repository()

        val result = repository.getUserPhoto(session, "u1")

        Assert.assertTrue(result.isFailure)
    }
}
