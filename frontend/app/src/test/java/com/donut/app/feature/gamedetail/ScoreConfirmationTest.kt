package com.donut.app.feature.gamedetail

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.data.model.Round
import com.donut.app.core.data.model.ScoreProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScoreConfirmationTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private val alice = Player("u1", "alice", "Alice A", 1500.0, 1)
    private val bob = Player("u2", "bob", "Bob B", 1500.0, 2)
    private val carol = Player("u3", "carol", "Carol C", 1500.0, 1)
    private val dave = Player("u4", "dave", "Dave D", 1500.0, 2)

    // Alice reported 6-4 3-6 7-5, a win for team 1
    private val reported = listOf(Round(1, 6, 4), Round(2, 3, 6), Round(3, 7, 5))

    private fun confirmed(player: Player) = player.copy(scoreConfirmedAt = now)

    private fun game(
        players: List<Player> = listOf(confirmed(alice), bob),
        proposal: List<Round>? = reported,
        status: GameStatus = GameStatus.IN_PROGRESS,
        format: Format = Format.SINGLES
    ) = Game(
        id = "game-1",
        format = format,
        status = status,
        source = GameSource.SCHEDULED,
        startsAt = now.minusSeconds(3600),
        court = Court(1, "Stanley Park", 49.3, -123.14),
        players = players,
        rounds = emptyList(),
        scoreProposal = proposal?.let { ScoreProposal(it, alice.id, now) }
    )

    // Input: Alice reported the score of a started game, Bob hasn't agreed yet
    // Expected output: Bob can confirm it or suggest a correction; Alice can only change it
    @Test
    fun onlyPlayersWhoHaveNotAgreedCanConfirm() {
        val bobActions = gameActions(game(), "bob", now)
        assertTrue(bobActions.canConfirmScore)
        assertTrue(bobActions.canReportScore)

        val aliceActions = gameActions(game(), "alice", now)
        assertFalse(aliceActions.canConfirmScore)
        assertTrue(aliceActions.canReportScore)
    }

    // Input: started game with no score reported, a completed game, and a viewer who isn't playing
    // Expected output: nobody can confirm
    @Test
    fun nothingToConfirmWithoutAScoreWaitingOrForOutsiders() {
        assertFalse(gameActions(game(proposal = null), "bob", now).canConfirmScore)
        assertFalse(gameActions(game(status = GameStatus.COMPLETED), "bob", now).canConfirmScore)
        assertFalse(gameActions(game(), "carol", now).canConfirmScore)
    }

    // Input: doubles game where Alice reported and Carol confirmed
    // Expected output: Bob and Dave still have to, in team order; nobody waits without a score
    @Test
    fun playersYetToConfirmAreThoseWithoutAnAgreement() {
        val doubles = game(
            players = listOf(confirmed(alice), confirmed(carol), bob, dave),
            format = Format.DOUBLES
        )

        assertEquals(listOf(bob, dave), playersYetToConfirm(doubles))
        assertEquals(emptyList<Player>(), playersYetToConfirm(game(proposal = null)))
    }

    // Input: one, two and three players still to confirm
    // Expected output: their names joined with commas and "and"
    @Test
    fun waitingForTextListsTheNames() {
        assertEquals("Waiting for Bob B to confirm.", waitingForText(listOf(bob)))
        assertEquals("Waiting for Bob B and Dave D to confirm.", waitingForText(listOf(bob, dave)))
        assertEquals(
            "Waiting for Bob B, Carol C and Dave D to confirm.",
            waitingForText(listOf(bob, carol, dave))
        )
    }

    // Input: a team 1 win, and a team 2 win
    // Expected output: the winners' names, with each set's score from the winners' side
    @Test
    fun confirmScoreTextIsFromTheWinnersSide() {
        assertEquals("Alice A won 6–4, 3–6, 7–5.", confirmScoreText(game(), reported))

        val team2Win = listOf(Round(1, 4, 6), Round(2, 2, 6))
        assertEquals("Bob B won 6–4, 6–2.", confirmScoreText(game(), team2Win))
    }

    // Input: the score form opened with no score waiting, by a player who hasn't agreed, and by the reporter
    // Expected output: "Final score", "Suggest a correction" and "Change score"
    @Test
    fun scoreFormTitleFollowsWhoIsReporting() {
        assertEquals("Final score", scoreFormText(game(proposal = null), canConfirm = false).title)
        assertEquals("Suggest a correction", scoreFormText(game(), canConfirm = true).title)
        assertEquals("Change score", scoreFormText(game(), canConfirm = false).title)
    }
}
