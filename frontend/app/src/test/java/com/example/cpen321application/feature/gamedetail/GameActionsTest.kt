package com.example.cpen321application.feature.gamedetail

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GameActionsTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private val alice = Player("u1", "alice", "Alice A", 1500.0, 1)
    private val bob = Player("u2", "bob", "Bob B", 1500.0, 2)
    private val carol = Player("u3", "carol", "Carol C", 1500.0, 1)

    private fun game(
        players: List<Player> = listOf(alice),
        format: Format = Format.SINGLES,
        status: GameStatus = GameStatus.OPEN,
        source: GameSource = GameSource.SCHEDULED,
        startsIn: Duration = Duration.ofHours(2)
    ) = Game(
        id = "game-1",
        format = format,
        status = status,
        source = source,
        startsAt = now.plus(startsIn),
        court = Court(1, "Stanley Park", 49.3, -123.14),
        players = players,
        rounds = emptyList()
    )

    // Input: open scheduled singles game with one player on team 1, viewed by someone else
    // Expected output: they can join team 2 only, and can't change the game
    @Test
    fun otherUserCanJoinTheTeamWithASpot() {
        val actions = gameActions(game(), "bob", now)

        assertFalse(actions.isPlayer)
        assertEquals(listOf(2), actions.joinableTeams)
        assertFalse(actions.canLeave)
        assertFalse(actions.canChangeTimeOrCourt)
        assertFalse(actions.canCancel)
    }

    // Input: open doubles game with one player on each team
    // Expected output: both teams can be joined
    @Test
    fun doublesTeamsHaveTwoSpots() {
        val actions = gameActions(game(players = listOf(alice, bob), format = Format.DOUBLES), "carol", now)

        assertEquals(listOf(1, 2), actions.joinableTeams)
    }

    // Input: games the user can't join: already in it, full, matched, started, or completed
    // Expected output: no joinable team
    @Test
    fun cannotJoinClosedOrFullGames() {
        assertEquals(emptyList<Int>(), gameActions(game(), "alice", now).joinableTeams)
        assertEquals(
            emptyList<Int>(),
            gameActions(game(players = listOf(alice, bob), status = GameStatus.BOOKED), "carol", now).joinableTeams
        )
        assertEquals(emptyList<Int>(), gameActions(game(source = GameSource.MATCHED), "bob", now).joinableTeams)
        assertEquals(emptyList<Int>(), gameActions(game(startsIn = Duration.ZERO), "bob", now).joinableTeams)
        assertEquals(emptyList<Int>(), gameActions(game(status = GameStatus.COMPLETED), "bob", now).joinableTeams)
    }

    // Input: player in an upcoming game that starts in 2 hours
    // Expected output: they can leave, change time or court, and cancel, but not report a score yet
    @Test
    fun playerCanEditAnUpcomingGame() {
        val actions = gameActions(game(players = listOf(alice, bob)), "alice", now)

        assertTrue(actions.isPlayer)
        assertTrue(actions.canLeave)
        assertFalse(actions.leaveTooLate)
        assertTrue(actions.canChangeTimeOrCourt)
        assertTrue(actions.canCancel)
        assertFalse(actions.canReportScore)
    }

    // Input: player in a game that starts in 29 minutes, and one that starts in exactly 30
    // Expected output: they can't leave the first (it says why), but can leave the second
    @Test
    fun cannotLeaveWithinThirtyMinutesOfTheStart() {
        val tooLate = gameActions(game(startsIn = Duration.ofMinutes(29)), "alice", now)
        assertFalse(tooLate.canLeave)
        assertTrue(tooLate.leaveTooLate)

        val justInTime = gameActions(game(startsIn = Duration.ofMinutes(30)), "alice", now)
        assertTrue(justInTime.canLeave)
        assertFalse(justInTime.leaveTooLate)
    }

    // Input: player in a booked game that already started
    // Expected output: they can report the score, but can't move the game or leave
    @Test
    fun playerCanReportTheScoreOnceTheGameStarted() {
        val actions = gameActions(
            game(players = listOf(alice, bob), status = GameStatus.BOOKED, startsIn = Duration.ofMinutes(-45)),
            "bob",
            now
        )

        assertTrue(actions.canReportScore)
        assertFalse(actions.canChangeTimeOrCourt)
        assertFalse(actions.canLeave)
        assertTrue(actions.leaveTooLate)
    }

    // Input: player in a completed or cancelled game
    // Expected output: nothing can be changed any more
    @Test
    fun closedGamesCannotChange() {
        for (status in listOf(GameStatus.COMPLETED, GameStatus.CANCELLED)) {
            val actions = gameActions(game(status = status, startsIn = Duration.ofHours(-2)), "alice", now)

            assertTrue(actions.isPlayer)
            assertFalse(actions.canReportScore)
            assertFalse(actions.canChangeTimeOrCourt)
            assertFalse(actions.canLeave)
            assertFalse(actions.leaveTooLate)
            assertFalse(actions.canCancel)
        }
    }

    // Input: no signed-in user
    // Expected output: not a player, so no player actions
    @Test
    fun signedOutUserIsNotAPlayer() {
        val actions = gameActions(game(players = listOf(alice, carol)), null, now)

        assertFalse(actions.isPlayer)
        assertFalse(actions.canCancel)
    }
}
