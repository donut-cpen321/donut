package com.example.cpen321application.feature.tournamentdetail

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.MatchGame
import com.example.cpen321application.core.data.model.Round
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentCreator
import com.example.cpen321application.core.data.model.TournamentMatch
import com.example.cpen321application.core.data.model.TournamentPlayer
import com.example.cpen321application.core.data.model.TournamentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BracketColumnsTest {
    private val startsAt = Instant.parse("2026-11-02T17:00:00Z")
    private val court = Court(1, "Stanley Park", 49.3, -123.14)

    private val singlesPlayers = (1..8).map { seed ->
        TournamentPlayer("u$seed", "user$seed", "Seed $seed", 1500.0, seed)  // one player per seed in singles
    }

    private fun tournament(
        matches: List<TournamentMatch>,
        players: List<TournamentPlayer> = singlesPlayers,
        format: Format = Format.SINGLES
    ) = Tournament(
        id = "t1",
        name = "Sunday Smash",
        format = format,
        status = TournamentStatus.IN_PROGRESS,
        startsAt = startsAt,
        court = court,
        creator = TournamentCreator("u1", "user1", "Seed 1", 1500.0),
        capacity = players.size,
        players = players,
        matches = matches
    )

    private fun game(id: String, vararg rounds: Round) =
        MatchGame(id, GameStatus.COMPLETED, startsAt, court, rounds.toList())

    private val quarterfinals = listOf(1 to 8, 4 to 5, 3 to 6, 2 to 7).mapIndexed { position, (team1, team2) ->
        // Right after the draw: games booked, no winners
        TournamentMatch(1, position, team1, team2, null, game("qf$position"))
    }

    private val laterRounds = listOf(  // the semifinals and final before any quarterfinal is decided
        TournamentMatch(2, 0, null, null, null, null),
        TournamentMatch(2, 1, null, null, null, null),
        TournamentMatch(3, 0, null, null, null, null)
    )

    // Input: a freshly drawn singles bracket
    // Expected output: 3 columns titled Quarterfinals, Semifinals and Final with 4, 2 and 1 matches
    @Test
    fun buildsThreeColumnsWithTheRightNumberOfMatches() {
        val columns = bracketColumns(tournament(quarterfinals + laterRounds))

        assertEquals(listOf(1, 2, 3), columns.map { it.round })
        assertEquals(listOf("Quarterfinals", "Semifinals", "Final"), columns.map { it.title })
        assertEquals(listOf(4, 2, 1), columns.map { it.matches.size })
        assertEquals(listOf(0, 1, 2, 3), columns[0].matches.map { it.position })
    }

    // Input: a freshly drawn singles bracket
    // Expected output: quarterfinals show seeds and names in bracket order, with their game IDs and no scores
    @Test
    fun quarterfinalsShowSeedsAndNames() {
        val quarterfinalColumn = bracketColumns(tournament(quarterfinals + laterRounds))[0]

        val first = quarterfinalColumn.matches[0]
        assertEquals(1, first.team1.seed)
        assertEquals("Seed 1", first.team1.name)
        assertEquals(8, first.team2.seed)
        assertEquals("Seed 8", first.team2.name)
        assertEquals("qf0", first.gameId)
        assertTrue(first.team1.scores.isEmpty())
        assertFalse(first.team1.isWinner)

        assertEquals(listOf(4, 3, 2), quarterfinalColumn.matches.drop(1).map { it.team1.seed })
        assertEquals(listOf(5, 6, 7), quarterfinalColumn.matches.drop(1).map { it.team2.seed })
    }

    // Input: semifinals and final with no teams yet
    // Expected output: TBD against TBD, with no seed and no game to open
    @Test
    fun undecidedMatchesShowTbd() {
        val final = bracketColumns(tournament(quarterfinals + laterRounds))[2].matches.single()

        assertNull(final.team1.seed)
        assertEquals(TBD_TEAM_NAME, final.team1.name)
        assertEquals(TBD_TEAM_NAME, final.team2.name)
        assertNull(final.gameId)
    }

    // Input: quarterfinal 1v8 won by seed 1, 6-4 3-6 7-5; seed 1 waits in the first semifinal
    // Expected output: each team's set scores in order (team1Score to match.team1), seed 1 marked as winner,
    // and the semifinal shows seed 1 against TBD
    @Test
    fun decidedMatchesShowScoresAndTheWinner() {
        val played = quarterfinals[0].copy(
            winner = 1,
            game = game("qf0", Round(1, 6, 4), Round(2, 3, 6), Round(3, 7, 5))
        )
        val semifinal = TournamentMatch(2, 0, 1, null, null, null)
        val columns = bracketColumns(tournament(listOf(played) + quarterfinals.drop(1) + semifinal))

        val match = columns[0].matches[0]
        assertEquals(listOf(6, 3, 7), match.team1.scores)
        assertEquals(listOf(4, 6, 5), match.team2.scores)
        assertTrue(match.team1.isWinner)
        assertFalse(match.team2.isWinner)

        val waiting = columns[1].matches[0]
        assertEquals("Seed 1", waiting.team1.name)
        assertEquals(TBD_TEAM_NAME, waiting.team2.name)
        assertNull(waiting.gameId)
    }

    // Input: a doubles bracket where seed 1 is Alice and Bob
    // Expected output: the team is named after both players
    @Test
    fun doublesTeamsAreNamedAfterBothPlayers() {
        val players = listOf(
            TournamentPlayer("u1", "alice", "Alice A", 1600.0, 1),
            TournamentPlayer("u2", "bob", "Bob B", 1400.0, 1)
        )
        val match = TournamentMatch(1, 0, 1, 8, null, null)

        val slot = bracketColumns(tournament(listOf(match), players, Format.DOUBLES))[0].matches[0].team1

        assertEquals("Alice A & Bob B", slot.name)
    }

    // Input: a tournament the backend sent without matches (before the draw)
    // Expected output: still 3 columns of 4, 2 and 1 matches, all TBD
    @Test
    fun missingMatchesShowAsTbd() {
        val columns = bracketColumns(tournament(emptyList()))

        assertEquals(listOf(4, 2, 1), columns.map { it.matches.size })
        assertTrue(columns.flatMap { it.matches }.all { it.team1.name == TBD_TEAM_NAME && it.gameId == null })
    }
}
