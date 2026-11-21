package com.donut.app.feature.gamedetail

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.network.dto.FriendDto
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

    // Input: player in a game a player started 45 minutes ago
    // Expected output: they can report the score, but can't move the game or leave
    @Test
    fun playerCanReportTheScoreOnceTheGameStarted() {
        val actions = gameActions(
            game(players = listOf(alice, bob), status = GameStatus.IN_PROGRESS, startsIn = Duration.ofMinutes(-45)),
            "bob",
            now
        )

        assertTrue(actions.canReportScore)
        assertFalse(actions.canChangeTimeOrCourt)
        assertFalse(actions.canLeave)
        assertTrue(actions.leaveTooLate)
    }

    // Input: player in a booked scheduled, matched or tournament game that starts in 2 hours
    // Expected output: they can start it now, but can't report the score until it has started
    @Test
    fun playerCanStartABookedGameEarly() {
        for (source in listOf(GameSource.SCHEDULED, GameSource.MATCHED, GameSource.TOURNAMENT)) {
            val actions = gameActions(
                game(players = listOf(alice, bob), status = GameStatus.BOOKED, source = source),
                "alice",
                now
            )

            assertTrue(actions.canStartNow)
            assertFalse(actions.canReportScore)
        }
    }

    // Input: the same game once a player started it (as it shows after Start now)
    // Expected output: Report final score replaces Start now
    @Test
    fun startedGameOffersTheScoreInsteadOfStartNow() {
        val actions = gameActions(
            game(players = listOf(alice, bob), status = GameStatus.IN_PROGRESS, startsIn = Duration.ZERO),
            "alice",
            now
        )

        assertFalse(actions.canStartNow)
        assertTrue(actions.canReportScore)
    }

    // Input: games that can't start early: viewed by a non-player or signed out, open (free spots or a match
    // waiting for answers), completed, or cancelled
    // Expected output: no Start now
    @Test
    fun cannotStartGamesThatAreNotBookedOrNotYours() {
        val booked = game(players = listOf(alice, bob), status = GameStatus.BOOKED)
        val unansweredMatch = game(players = listOf(alice, bob), source = GameSource.MATCHED)

        assertFalse(gameActions(booked, "carol", now).canStartNow)
        assertFalse(gameActions(booked, null, now).canStartNow)
        assertFalse(gameActions(game(players = listOf(alice)), "alice", now).canStartNow)
        assertFalse(gameActions(unansweredMatch, "alice", now).canStartNow)
        assertFalse(gameActions(game(status = GameStatus.COMPLETED), "alice", now).canStartNow)
        assertFalse(gameActions(game(status = GameStatus.CANCELLED), "alice", now).canStartNow)
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

    // Input: player in a booked tournament game that starts in 2 hours
    // Expected output: they can't leave or cancel it (no "too late" note either), but can move it
    @Test
    fun playerCannotLeaveOrCancelATournamentGame() {
        val actions = gameActions(
            game(players = listOf(alice, bob), status = GameStatus.BOOKED, source = GameSource.TOURNAMENT),
            "alice",
            now
        )

        assertTrue(actions.isPlayer)
        assertFalse(actions.canLeave)
        assertFalse(actions.leaveTooLate)
        assertFalse(actions.canCancel)
        assertTrue(actions.canChangeTimeOrCourt)
        assertFalse(actions.canReportScore)
    }

    // Input: player in a tournament game that was started 45 minutes ago
    // Expected output: they can report the score, which advances the bracket, but still can't leave or cancel
    @Test
    fun playerCanReportTheScoreOfAStartedTournamentGame() {
        val actions = gameActions(
            game(
                players = listOf(alice, bob),
                status = GameStatus.IN_PROGRESS,
                source = GameSource.TOURNAMENT,
                startsIn = Duration.ofMinutes(-45)
            ),
            "bob",
            now
        )

        assertTrue(actions.canReportScore)
        assertFalse(actions.canLeave)
        assertFalse(actions.leaveTooLate)
        assertFalse(actions.canCancel)
    }

    // Input: someone who is not playing views a tournament game with a free-looking team
    // Expected output: they can't join it, since the bracket decides who plays
    @Test
    fun nobodyCanJoinATournamentGame() {
        val actions = gameActions(game(source = GameSource.TOURNAMENT), "carol", now)

        assertEquals(emptyList<Int>(), actions.joinableTeams)
    }

    // Input: open matched game, viewed by one of its players
    // Expected output: they can cancel, but can't leave or move the game
    @Test
    fun matchedGamePlayerCanCancelButNotLeaveOrMove() {
        val actions = gameActions(game(players = listOf(alice, bob), source = GameSource.MATCHED), "alice", now)

        assertTrue(actions.canCancel)
        assertFalse(actions.canLeave)
        assertFalse(actions.leaveTooLate)
        assertFalse(actions.canChangeTimeOrCourt)
    }

    // Input: player in an open scheduled game that has not started
    // Expected output: they can invite friends
    @Test
    fun playerCanInviteFriendsToAnOpenGame() {
        assertTrue(gameActions(game(), "alice", now).canInvite)
    }

    // Input: games a friend could not join: viewed by a non-player, booked, matched, tournament, started, completed
    // Expected output: no invites
    @Test
    fun cannotInviteToGamesFriendsCannotJoin() {
        assertFalse(gameActions(game(), "bob", now).canInvite)
        assertFalse(gameActions(game(), null, now).canInvite)
        assertFalse(
            gameActions(game(players = listOf(alice, bob), status = GameStatus.BOOKED), "alice", now).canInvite
        )
        assertFalse(gameActions(game(source = GameSource.MATCHED), "alice", now).canInvite)
        assertFalse(gameActions(game(source = GameSource.TOURNAMENT), "alice", now).canInvite)
        assertFalse(gameActions(game(startsIn = Duration.ZERO), "alice", now).canInvite)
        assertFalse(gameActions(game(status = GameStatus.COMPLETED), "alice", now).canInvite)
    }

    // Input: a booked game whose start time passed 10 minutes ago, which nobody started
    // Expected output: it doesn't start on its own: Start now is still offered, the score isn't yet
    @Test
    fun bookedGameDoesNotStartOnItsOwn() {
        val late = game(players = listOf(alice, bob), status = GameStatus.BOOKED, startsIn = Duration.ofMinutes(-10))

        val actions = gameActions(late, "alice", now)

        assertTrue(actions.canStartNow)
        assertFalse(actions.canReportScore)
    }

    // Input: a game a player started, whose start time is still ahead by this device's clock
    // (the clock is behind the backend's)
    // Expected output: it counts as started: the score can be reported, it can't be started again,
    // and there is no time left to wait for
    @Test
    fun inProgressGameHasStartedWhateverThisClockSays() {
        val inProgress = game(
            players = listOf(alice, bob),
            status = GameStatus.IN_PROGRESS,
            startsIn = Duration.ofMillis(500)
        )

        val actions = gameActions(inProgress, "alice", now)

        assertTrue(actions.canReportScore)
        assertFalse(actions.canStartNow)
        assertFalse(actions.canChangeTimeOrCourt)
        assertEquals(null, nextActionsChange(inProgress, now))
    }

    // Input: games two hours away, 10 minutes away, already started, and completed
    // Expected output: the leave deadline, the start time, then nothing left to wait for
    @Test
    fun nextActionsChangeIsTheNextTimeRule() {
        val later = game(startsIn = Duration.ofHours(2))
        val soon = game(startsIn = Duration.ofMinutes(10))

        assertEquals(later.startsAt.minus(Duration.ofMinutes(30)), nextActionsChange(later, now))
        assertEquals(soon.startsAt, nextActionsChange(soon, now))
        assertEquals(null, nextActionsChange(game(startsIn = Duration.ZERO), now))
        assertEquals(null, nextActionsChange(game(status = GameStatus.COMPLETED, startsIn = Duration.ofHours(2)), now))
    }

    // Input: friends bob, who is already playing, and dave, who is not
    // Expected output: only dave can be invited
    @Test
    fun friendsInTheGameAreNotInvitable() {
        val friends = listOf(FriendDto(bob.id, bob.username), FriendDto("u4", "dave"))

        val invitable = invitableFriends(friends, game(players = listOf(alice, bob)))

        assertEquals(listOf("u4"), invitable.map { it.userId })
    }
}
