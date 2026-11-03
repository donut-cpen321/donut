package com.example.cpen321application.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MatchResultTest {

    private fun player(id: String, team: Int, before: Double? = null, after: Double? = null) =
        Player(id = id, username = "$id@example.com", name = id, rating = after ?: 1500.0, team = team,
            ratingBefore = before, ratingAfter = after)

    private fun game(players: List<Player>, rounds: List<Round>, format: Format = Format.SINGLES) = Game(
        id = "g1",
        format = format,
        status = GameStatus.COMPLETED,
        source = GameSource.SCHEDULED,
        startsAt = Instant.parse("2026-11-03T18:45:00Z"),
        court = Court(1, "Almond Park", 49.0, -123.0),
        players = players,
        rounds = rounds
    )

    @Test
    fun winnerOfSinglesSeesWinFromTheirSide() {
        val game = game(
            players = listOf(player("ana", 1, 1500.0, 1516.0), player("ben", 2, 1500.0, 1484.0)),
            rounds = listOf(Round(1, 6, 4), Round(2, 3, 6), Round(3, 7, 5))
        )

        val result = game.resultFor("ana@example.com")!!

        assertEquals(MatchOutcome.WIN, result.outcome)
        assertEquals(listOf("ben"), result.opponents.map { it.id })
        assertEquals(emptyList<Player>(), result.partners)
        assertEquals(listOf(6 to 4, 3 to 6, 7 to 5), result.myScores)
        assertEquals(16, result.ratingChange)
    }

    @Test
    fun loserOnTeamTwoSeesScoresFlipped() {
        val game = game(
            players = listOf(player("ana", 1, 1500.0, 1516.0), player("ben", 2, 1500.0, 1484.0)),
            rounds = listOf(Round(1, 6, 4), Round(2, 6, 2))
        )

        val result = game.resultFor("ben@example.com")!!

        assertEquals(MatchOutcome.LOSS, result.outcome)
        assertEquals(listOf(4 to 6, 2 to 6), result.myScores)
        assertEquals(-16, result.ratingChange)
    }

    @Test
    fun doublesSplitsPartnersFromOpponents() {
        val game = game(
            players = listOf(player("ana", 1), player("cal", 1), player("ben", 2), player("dee", 2)),
            rounds = listOf(Round(1, 2, 6)),
            format = Format.DOUBLES
        )

        val result = game.resultFor("ana@example.com")!!

        assertEquals(listOf("cal"), result.partners.map { it.id })
        assertEquals(listOf("ben", "dee"), result.opponents.map { it.id })
        assertEquals(MatchOutcome.LOSS, result.outcome)
        assertNull(result.ratingChange)
    }

    @Test
    fun returnsNullForSomeoneWhoDidNotPlay() {
        val game = game(players = listOf(player("ana", 1), player("ben", 2)), rounds = listOf(Round(1, 6, 0)))

        assertNull(game.resultFor("zed@example.com"))
    }

    @Test
    fun noWinnerWithoutRounds() {
        val game = game(players = listOf(player("ana", 1), player("ben", 2)), rounds = emptyList())

        assertNull(game.winningTeam)
        assertNull(game.resultFor("ana@example.com")!!.outcome)
    }
}
