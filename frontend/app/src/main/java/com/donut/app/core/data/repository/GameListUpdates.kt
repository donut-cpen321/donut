package com.donut.app.core.data.repository

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.network.EventStreamApi
import com.donut.app.core.network.RetrofitClient
import com.donut.app.core.network.ServerSentEvent
import com.donut.app.core.network.dto.GameDto
import com.donut.app.core.network.dto.TournamentDto
import com.donut.app.core.network.dto.toGame
import com.donut.app.core.network.dto.toTournament
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

// What the backend pushes about the Games tab
sealed interface GameListUpdate {
    val games: List<Game>
    val tournaments: List<Tournament>

    // Games and tournaments open to join, soonest first, with their forecast. The same for everyone
    data class Feed(override val games: List<Game>, override val tournaments: List<Tournament>) : GameListUpdate

    // The signed-in user's games and tournaments
    data class Mine(override val games: List<Game>, override val tournaments: List<Tournament>) : GameListUpdate
}

interface GameListUpdates {
    // The Games tab's lists pushed by the backend: both first, then each again whenever it changes (a game or
    // tournament created, joined, left, started, scored or cancelled, by anyone). Ends right away if signed out.
    // Throws IOException if the connection drops, HttpException if the backend refuses the stream
    fun updates(): Flow<GameListUpdate>
}

// The event's data: { games: [...], tournaments: [...] }, shaped like GET /games/feed and GET /tournaments/feed
private data class GameListDto(val games: List<GameDto>?, val tournaments: List<TournamentDto>?)

class GameListUpdatesImpl(
    private val session: AuthSession,
    private val eventStreamApi: EventStreamApi = RetrofitClient.eventStreamApi,
    private val gson: Gson = Gson()
) : GameListUpdates {
    override fun updates(): Flow<GameListUpdate> =
        session.liveEvents(eventStreamApi, "games/feed/events", read = ::toUpdate)

    // Null for events this version of the app doesn't know
    private fun toUpdate(event: ServerSentEvent): GameListUpdate? =
        when (event.name) {
            "feed" -> parse(event.data).let { (games, tournaments) -> GameListUpdate.Feed(games, tournaments) }
            "mine" -> parse(event.data).let { (games, tournaments) -> GameListUpdate.Mine(games, tournaments) }
            else -> null
        }

    private fun parse(json: String): Pair<List<Game>, List<Tournament>> {
        val lists = gson.fromJson(json, GameListDto::class.java)
        return lists.games.orEmpty().map { it.toGame() } to lists.tournaments.orEmpty().map { it.toTournament() }
    }
}
