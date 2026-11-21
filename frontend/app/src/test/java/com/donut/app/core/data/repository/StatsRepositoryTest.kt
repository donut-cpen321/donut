package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.StatsApi
import com.donut.app.core.network.dto.OpponentRatingBandDto
import com.donut.app.core.network.dto.RatingPointDto
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * StatsRepository against a fake backend (MockWebServer), so no emulator is needed.
 * The JSON is shaped like the backend's GET /stats response.
 */
class StatsRepositoryTest {
    private val signedIn = User("alice@example.com", "access", "refresh")
    private val otherUserId = "7c2e9b14-3f5a-4d6b-8e7c-0a1b2c3d4e5f"

    private val statsJson = """
        {
          "stats": {
            "user": { "id": "u1", "username": "alice@example.com", "name": "Alice A", "rating": 1520.5, "rank": 4 },
            "sport": "TENNIS",
            "gamesPlayed": 1, "wins": 1, "losses": 0,
            "setsPlayed": 2, "setsWon": 2, "setsLost": 0,
            "pointsWon": 12, "pointsLost": 7,
            "ratingHistory": [
              { "gameId": "g1", "playedAt": "2026-11-10T17:00:00.000Z", "ratingBefore": 1500, "ratingAfter": 1520.5 }
            ],
            "vsOpponentRating": [
              { "minRating": 1400, "maxRating": 1500, "games": 1, "wins": 1, "losses": 0, "averageMargin": 5 }
            ]
          }
        }
    """.trimIndent()

    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
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

    private fun repository(): StatsRepository {
        val session = AuthSession(retrofit.create(ApiService::class.java), signedIn) {}
        return StatsRepositoryImpl(session, retrofit.create(StatsApi::class.java))
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS)!!

    // Input: GET /stats returns one rated game
    // Expected behavior: GET without a userId, with the access token as a Bearer token
    // Expected output: the totals, the rating history and the opponent band parsed
    @Test
    fun getStatsParsesTheCallersStats() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(statsJson))

        val stats = repository().getStats().getOrThrow()

        val request = takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/stats", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))

        assertEquals(1520.5, stats.user?.rating ?: 0.0, 1e-9)
        assertEquals(4, stats.user?.rank)
        assertEquals(1, stats.gamesPlayed)
        assertEquals(12, stats.pointsWon)
        assertEquals(listOf(RatingPointDto("g1", "2026-11-10T17:00:00.000Z", 1500.0, 1520.5)), stats.ratingHistory)
        assertEquals(listOf(OpponentRatingBandDto(1400, 1500, 1, 1, 0, 5.0)), stats.vsOpponentRating)
    }

    // Input: a userId
    // Expected behavior: the userId is sent as a query parameter
    @Test
    fun getStatsSendsTheUserId() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(statsJson))

        repository().getStats(otherUserId).getOrThrow()

        assertEquals("/stats?userId=$otherUserId", takeRequest().path)
    }

    // Input: GET /stats returns 404
    // Expected output: a failure holding the HttpException
    @Test
    fun getStatsFailsOnAnHttpError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "User not found" }"""))

        val result = repository().getStats(otherUserId)

        assertTrue(result.exceptionOrNull() is HttpException)
    }
}
