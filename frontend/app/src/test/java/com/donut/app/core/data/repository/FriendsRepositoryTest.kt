package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.FriendsApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FriendsRepositoryTest {
    private val server = MockWebServer()
    private val signedInUser = User("alice", "access_token", "refresh_token")

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(): FriendsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return FriendsRepositoryImpl(
            apiService = retrofit.create(ApiService::class.java),
            friendsApi = retrofit.create(FriendsApi::class.java)
        )
    }

    private fun createSession(): AuthSession {
        val apiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        return AuthSession(apiService, signedInUser) {}
    }

    @Test
    fun getUserMeParsesTopLevelElo() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"u123","username":"alice","elo":1650}""")
        )

        val result = repository().getUserMe(createSession())
        assertTrue(result.isSuccess)
        val userMe = result.getOrNull()
        assertEquals("u123", userMe?.id)
        assertEquals("alice", userMe?.username)
        assertEquals(1650, userMe?.elo)
    }

    @Test
    fun getUserMeParsesRatingAndNestedUserObj() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"user":{"_id":"u456","username":"bob","rating":1720.0}}""")
        )

        val result = repository().getUserMe(createSession())
        assertTrue(result.isSuccess)
        val userMe = result.getOrNull()
        assertEquals("u456", userMe?.id)
        assertEquals("bob", userMe?.username)
        assertEquals(1720, userMe?.elo)
    }
}
