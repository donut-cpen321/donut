package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.TournamentStatus
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.TournamentApi
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
 * The Games tab's and a tournament's event streams, against a fake backend that sends events like the backend does
 */
class LiveListsTest {
    private val server = MockWebServer()

    private val court = """{ "id": 3, "name": "Kitsilano Beach", "latitude": 49.27, "longitude": -123.15 }"""

    private fun gameJson(id: String, status: String) = """{ "id": "$id", "format": "SINGLES", "status": "$status",
        "startsAt": "2026-11-02T18:00:00.000Z", "gameSource": "SCHEDULED", "court": $court,
        "players": [ { "id": "u1", "username": "alice", "name": "Alice A", "rating": 1500.5, "team": 1 } ],
        "rounds": [], "forecast": null, "tournament": null }"""

    private fun playerJson(id: String) = """{ "id": "$id", "username": "$id", "name": "$id", "rating": 1500 }"""

    private fun tournamentJson(status: String, playerIds: List<String>) = """{ "id": "t1", "name": "Fall Cup",
        "format": "SINGLES", "status": "$status", "startsAt": "2026-11-08T17:00:00.000Z", "capacity": 8,
        "winningTeam": null, "court": $court,
        "creator": { "id": "u1", "username": "alice", "name": "Alice A", "rating": 1500.5 },
        "players": [${playerIds.joinToString(transform = ::playerJson)}],
        "matches": [], "forecast": null }"""

    private fun tournamentEvent(status: String, playerIds: List<String>) =
        "event: tournamentUpdated\\ndata: { \"tournament\": ${tournamentJson(status, playerIds)} }\\n\\n"

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun session(): AuthSession {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        return AuthSession(api, User("alice", "access", "refresh")) {}
    }

    private fun events() =
        EventStreamApi(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), server.url("/"))

    private fun stream(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body.replace("\n", "")
            .replace("\\n", "\n")))
    }

    // Input: the Games tab stream's feed (one game, one tournament), "mine" (one booked game), then the feed again
    // Expected behavior: it connects to /games/feed/events with the access token
    // Expected output: Feed, Mine, Feed, each with its games and tournaments
    @Test
    fun gameListUpdates_readsFeedAndMine() = runBlocking {
        stream(
            "event: feed\\ndata: { \"games\": [${gameJson("g1", "OPEN")}], " +
                "\"tournaments\": [${tournamentJson("REGISTRATION", listOf("u1"))}] }\\n\\n" +
                "event: mine\\ndata: { \"games\": [${gameJson("g2", "BOOKED")}], \"tournaments\": [] }\\n\\n" +
                "event: feed\\ndata: { \"games\": [], \"tournaments\": [] }\\n\\n"
        )

        val updates = withTimeout(5_000) { GameListUpdatesImpl(session(), events()).updates().take(3).toList() }

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/games/feed/events", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        val feed = updates[0] as GameListUpdate.Feed
        assertEquals(listOf("g1"), feed.games.map { it.id })
        assertEquals(listOf("t1"), feed.tournaments.map { it.id })
        val mine = updates[1] as GameListUpdate.Mine
        assertEquals(GameStatus.BOOKED, mine.games.single().status)
        assertEquals(emptyList<String>(), mine.tournaments.map { it.id })
        assertEquals(GameListUpdate.Feed(emptyList(), emptyList()), updates[2])
    }

    // Input: a tournament's stream: one player registered, then a second, then the bracket drawn
    // Expected behavior: it connects to /tournaments/events with the tournament's ID
    // Expected output: the tournament three times, with its players and status as sent
    @Test
    fun tournamentUpdates_readsEachTournament() = runBlocking {
        stream(
            tournamentEvent("REGISTRATION", listOf("u1")) +
                ": ping\\n\\n" +
                tournamentEvent("REGISTRATION", listOf("u1", "u2")) +
                tournamentEvent("IN_PROGRESS", listOf("u1", "u2"))
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = TournamentsRepositoryImpl(session(), retrofit.create(TournamentApi::class.java), events())

        val tournaments = withTimeout(5_000) { repository.tournamentUpdates("t1").take(3).toList() }

        assertEquals("/tournaments/events?tournamentId=t1", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertEquals(listOf(1, 2, 2), tournaments.map { it.players.size })
        assertEquals(TournamentStatus.IN_PROGRESS, tournaments.last().status)
    }
}
