package com.example.cpen321application.core.notifications

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.network.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * PushTokenRepository against a fake backend (MockWebServer), so no emulator is needed.
 */
class PushTokenRepositoryTest {
    private val server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun repository(user: User? = User("alice", "access", "refresh")): PushTokenRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        return PushTokenRepository(AuthSession(api, user) {}, api)
    }

    // Input: a signed-in user registers their device token; backend answers 204
    // Expected behavior: PUT /user/me/fcm-token with the access token and the device token
    // Expected output: success
    @Test
    fun registerSendsTokenWithAuthorization() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().register("device-token")

        val request = server.takeRequest()
        assertTrue(result.isSuccess)
        assertEquals("PUT", request.method)
        assertEquals("/user/me/fcm-token", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals("device-token", JSONObject(request.body.readUtf8()).getString("token"))
    }

    // Input: nobody is signed in
    // Expected behavior: nothing is sent
    // Expected output: failure, no request
    @Test
    fun registerWhenSignedOutFails() = runBlocking {
        val result = repository(user = null).register("device-token")

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    // Input: backend answers 500
    // Expected behavior: the error is returned, not thrown
    // Expected output: failure
    @Test
    fun registerWhenBackendFailsFails() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(repository().register("device-token").isFailure)
    }
}
