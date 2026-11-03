package com.example.cpen321application.feature.scheduledgame

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.GameTournament
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentCreator
import com.example.cpen321application.core.data.model.TournamentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ScheduledGameUiStateTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private val court = Court(1, "Stanley Park", 49.3, -123.14)

    private fun game(id: String, startsIn: Duration, tournament: GameTournament? = null) = Game(
        id = id,
        format = Format.SINGLES,
        status = GameStatus.OPEN,
        source = if (tournament == null) GameSource.SCHEDULED else GameSource.TOURNAMENT,
        startsAt = now.plus(startsIn),
        court = court,
        players = emptyList(),
        rounds = emptyList(),
        tournament = tournament
    )

    private fun tournament(id: String, startsIn: Duration, status: TournamentStatus = TournamentStatus.REGISTRATION) =
        Tournament(
            id = id,
            name = "Tournament $id",
            format = Format.DOUBLES,
            status = status,
            startsAt = now.plus(startsIn),
            court = court,
            creator = TournamentCreator("u1", "alice", "Alice A", 1500.0),
            capacity = 16,
            players = emptyList()
        )

    // Input: two games (in 1 h and 3 h) and two tournaments (in 2 h and 4 h), each list sorted on its own
    // Expected output: one list alternating game, tournament, game, tournament, soonest first
    @Test
    fun feedItemsMixGamesAndTournamentsByStartTime() {
        val state = ScheduledGameUiState(
            feed = listOf(game("g1", Duration.ofHours(1)), game("g3", Duration.ofHours(3))),
            tournamentFeed = listOf(tournament("t2", Duration.ofHours(2)), tournament("t4", Duration.ofHours(4)))
        )

        assertEquals(
            listOf("game-g1", "tournament-t2", "game-g3", "tournament-t4"),
            state.feedItems.map { it.key }
        )
    }

    // Input: a game and a tournament that start at the same time
    // Expected output: the game comes first (the sort is stable), and the keys stay distinct
    @Test
    fun gamesComeFirstOnATie() {
        val items = mergeByStartTime(
            games = listOf(game("same", Duration.ofHours(2))),
            tournaments = listOf(tournament("same", Duration.ofHours(2)))
        )

        assertEquals(listOf("game-same", "tournament-same"), items.map { it.key })
        assertTrue(items[0] is FeedItem.GameItem)
        assertTrue(items[1] is FeedItem.TournamentItem)
    }

    // Input: My games with a tournament in progress (started 1 h ago), its semifinal in 30 min and a game tomorrow
    // Expected output: the tournament first, then the semifinal, then tomorrow's game
    @Test
    fun myItemsIncludeTournamentsAndTheirMatches() {
        val semifinal = game("sf", Duration.ofMinutes(30), GameTournament("t1", "Tournament t1", 2))
        val state = ScheduledGameUiState(
            myGames = listOf(game("tomorrow", Duration.ofDays(1)), semifinal),
            myTournaments = listOf(tournament("t1", Duration.ofHours(-1), TournamentStatus.IN_PROGRESS))
        )

        assertEquals(listOf("tournament-t1", "game-sf", "game-tomorrow"), state.myItems.map { it.key })
        assertEquals(semifinal, (state.myItems[1] as FeedItem.GameItem).game)
    }

    // Input: the feed and My games lists
    // Expected output: each tab only mixes its own lists
    @Test
    fun feedAndMyItemsStaySeparate() {
        val state = ScheduledGameUiState(
            feed = listOf(game("feed", Duration.ofHours(1))),
            myGames = listOf(game("mine", Duration.ofHours(1))),
            tournamentFeed = listOf(tournament("open", Duration.ofHours(1))),
            myTournaments = listOf(tournament("registered", Duration.ofHours(1)))
        )

        assertEquals(listOf("game-feed", "tournament-open"), state.feedItems.map { it.key })
        assertEquals(listOf("game-mine", "tournament-registered"), state.myItems.map { it.key })
    }

    // Input: nothing loaded yet
    // Expected output: both tabs are empty
    @Test
    fun emptyStateHasNoItems() {
        val state = ScheduledGameUiState()

        assertTrue(state.feedItems.isEmpty())
        assertTrue(state.myItems.isEmpty())
    }
}
