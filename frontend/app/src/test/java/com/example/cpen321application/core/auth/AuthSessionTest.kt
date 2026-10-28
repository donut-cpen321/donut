package com.example.cpen321application.core.auth

import com.example.cpen321application.core.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// A protected route for the tests, ApiService has none yet
private interface GamesApi {
    @GET("games")
    suspend fun games(@Header("Authorization") authorization: String): ResponseBody
}

/**
 * AuthSession against a fake backend (MockWebServer), so no emulator is needed.
 */
class AuthSessionTest {
    private val signedIn = User("alice", "old-access", "old-refresh")
    private val refreshed = User("alice", "new-access", "new-refresh")
    private val newTokensJson = """{"accessToken":"new-access","refreshToken":"new-refresh"}"""

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()

    // Every user the session reported through onUserChanged
    private val userChanges = CopyOnWriteArrayList<User?>()
    private val refreshCalls = AtomicInteger()
    private val requestBodies = CopyOnWriteArrayList<String>()

    @Before
    fun startServer() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestBodies.add(request.body.readUtf8())
                return routes[request.path]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    // Created on first use, after the server started
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun session(user: User? = signedIn) =
        AuthSession(retrofit.create(ApiService::class.java), user) { userChanges.add(it) }

    private suspend fun AuthSession.getGames() =
        authorized { retrofit.create(GamesApi::class.java).games(it) }?.string()

    private fun response(code: Int, body: String = "") = MockResponse().setResponseCode(code).setBody(body)

    private fun RecordedRequest.bearerToken() = getHeader("Authorization")?.removePrefix("Bearer ")

    // A protected route that only accepts the refreshed access token
    private fun protectedRoute() {
        routes["/games"] = {
            if (it.bearerToken() == "new-access") response(200, "ok") else response(401)
        }
    }

    private fun refreshRoute(code: Int, body: String = "") {
        routes["/user/refresh"] = {
            refreshCalls.incrementAndGet()
            response(code, body)
        }
    }

    // Input: signed-in session, protected route accepts the current access token
    // Expected behavior: the access token is sent as a Bearer token, no refresh
    // Expected output: the route's 200 response
    @Test
    fun sendsTheAccessTokenAsBearer() = runBlocking {
        routes["/games"] = {
            if (it.bearerToken() == "old-access") response(200, "ok") else response(401)
        }
        refreshRoute(200, newTokensJson)

        val result = session().getGames()

        assertEquals("ok", result)
        assertEquals(0, refreshCalls.get())
    }

    // Input: expired access token, refresh token still valid
    // Expected behavior: POST /user/refresh with the old refresh token, both new tokens saved, request retried once
    // Expected output: the retried request's 200 response
    @Test
    fun refreshesAndRetriesWhenTheAccessTokenExpired() = runBlocking {
        protectedRoute()
        refreshRoute(200, newTokensJson)
        val session = session()

        val result = session.getGames()

        assertEquals("ok", result)
        assertEquals(refreshed, session.user)
        assertEquals(listOf(refreshed), userChanges)
        assertEquals("old-refresh", JSONObject(requestBodies[1]).getString("refreshToken"))
    }

    // Input: expired access token, and /user/refresh rejects the refresh token (used, expired or logged out)
    // Expected behavior: the user is signed out so the app goes back to Google sign-in
    // Expected output: the original 401 is thrown
    @Test
    fun signsOutWhenTheRefreshTokenIsRejected() = runBlocking {
        protectedRoute()
        refreshRoute(401, """{"error":"Invalid or expired refresh token"}""")
        val session = session()

        val error = assertThrows(HttpException::class.java) { runBlocking { session.getGames() } }

        assertEquals(401, error.code())
        assertNull(session.user)
        assertEquals(listOf<User?>(null), userChanges)
    }

    // Input: expired access token, and /user/refresh fails with a server error
    // Expected behavior: the session is kept, a server outage must not sign the user out
    // Expected output: the original 401 is thrown
    @Test
    fun keepsTheSessionWhenRefreshHasAServerError() = runBlocking {
        protectedRoute()
        refreshRoute(500)
        val session = session()

        val error = assertThrows(HttpException::class.java) { runBlocking { session.getGames() } }

        assertEquals(401, error.code())
        assertEquals(signedIn, session.user)
        assertEquals(emptyList<User?>(), userChanges)
    }

    // Input: two requests that both get a 401 with the same expired access token at the same time
    // Expected behavior: only one /user/refresh call (a second one would reuse a deleted refresh token and sign the user out)
    // Expected output: both requests succeed with the new access token
    @Test
    fun refreshesOnceForParallelExpiredRequests() = runBlocking {
        val bothArrived = CountDownLatch(2)
        routes["/games"] = {
            if (it.bearerToken() == "new-access") {
                response(200, "ok")
            } else {
                // Hold the 401 until both requests were sent with the old token
                bothArrived.countDown()
                bothArrived.await(5, TimeUnit.SECONDS)
                response(401)
            }
        }
        refreshRoute(200, newTokensJson)
        val session = session()

        val first = async(Dispatchers.IO) { session.getGames() }
        val second = async(Dispatchers.IO) { session.getGames() }

        assertEquals("ok", first.await())
        assertEquals("ok", second.await())
        assertEquals(1, refreshCalls.get())
    }

    // Input: no signed-in user
    // Expected behavior: nothing is sent to the backend
    // Expected output: null
    @Test
    fun sendsNothingWhenSignedOut() = runBlocking {
        protectedRoute()

        val result = session(user = null).getGames()

        assertNull(result)
        assertEquals(0, server.requestCount)
    }

    // Input: signed-in session, user taps Sign Out
    // Expected behavior: POST /user/logout with the refresh token, user cleared
    @Test
    fun logoutSendsTheRefreshTokenAndSignsOut() = runBlocking {
        routes["/user/logout"] = { response(204) }
        val session = session()

        session.logout()

        assertNull(session.user)
        assertEquals(listOf<User?>(null), userChanges)
        assertEquals("old-refresh", JSONObject(requestBodies.single()).getString("refreshToken"))
    }

    // Input: signed-in session, user taps Sign Out while the backend is unreachable
    // Expected behavior: the user is still signed out on this device, no crash
    @Test
    fun logoutSignsOutEvenWhenTheBackendIsUnreachable() = runBlocking {
        val session = session()
        server.shutdown()

        session.logout()

        assertNull(session.user)
        assertEquals(listOf<User?>(null), userChanges)
    }
}
