package com.example.cpen321application.feature.tournamentdetail

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentCreator
import com.example.cpen321application.core.data.model.TournamentPlayer
import com.example.cpen321application.core.data.model.TournamentStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TournamentActionsTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private val alice = TournamentPlayer("u1", "alice", "Alice A", 1500.0, null)
    private val bob = TournamentPlayer("u2", "bob", "Bob B", 1500.0, null)

    private fun others(count: Int) =  // more players, to fill the tournament after alice and bob
        (1..count).map { TournamentPlayer("id$it", "p$it", "P $it", 1500.0, null) }

    private fun tournament(
        players: List<TournamentPlayer> = listOf(alice, bob),
        status: TournamentStatus = TournamentStatus.REGISTRATION,
        startsIn: Duration = Duration.ofDays(1),
        capacity: Int = 8
    ) = Tournament(
        id = "t1",
        name = "Sunday Smash",
        format = Format.SINGLES,
        status = status,
        startsAt = now.plus(startsIn),
        court = Court(1, "Stanley Park", 49.3, -123.14),
        creator = TournamentCreator("u1", "alice", "Alice A", 1500.0),
        capacity = capacity,
        players = players
    )

    // Input: open tournament with a free spot, viewed by someone not registered
    // Expected output: they can join, but not leave or cancel
    @Test
    fun unregisteredUserCanJoinAnOpenTournament() {
        val actions = tournamentActions(tournament(), "carol", now)

        assertFalse(actions.isRegistered)
        assertFalse(actions.isCreator)
        assertTrue(actions.canJoin)
        assertFalse(actions.canLeave)
        assertFalse(actions.canCancel)
        assertFalse(actions.registrationClosed)
    }

    // Input: open tournament viewed by a registered player who did not create it
    // Expected output: they can leave, but not join again or cancel
    @Test
    fun registeredPlayerCanLeave() {
        val actions = tournamentActions(tournament(), "bob", now)

        assertTrue(actions.isRegistered)
        assertFalse(actions.isCreator)
        assertFalse(actions.canJoin)
        assertTrue(actions.canLeave)
        assertFalse(actions.canCancel)
    }

    // Input: open tournament viewed by its creator, who is registered automatically
    // Expected output: they can cancel it, but not leave it
    @Test
    fun creatorCanCancelButNotLeave() {
        val actions = tournamentActions(tournament(), "alice", now)

        assertTrue(actions.isRegistered)
        assertTrue(actions.isCreator)
        assertFalse(actions.canJoin)
        assertFalse(actions.canLeave)
        assertTrue(actions.canCancel)
    }

    // Input: open tournament with every spot taken, viewed by someone not registered
    // Expected output: they can't join
    @Test
    fun cannotJoinAFullTournament() {
        val full = tournament(players = listOf(alice, bob) + others(6))

        assertFalse(tournamentActions(full, "carol", now).canJoin)
    }

    // Input: doubles-sized tournament (capacity 16) with 8 players
    // Expected output: it still has spots, so someone can join
    @Test
    fun capacityDecidesWhetherThereIsASpot() {
        val doubles = tournament(players = listOf(alice, bob) + others(6), capacity = 16)

        assertTrue(tournamentActions(doubles, "carol", now).canJoin)
    }

    // Input: a tournament still open for registration whose start time has passed
    // Expected output: registration is closed, so nobody can join; the creator can still cancel
    @Test
    fun registrationClosesAtTheStartTime() {
        val started = tournament(startsIn = Duration.ofMinutes(-1))

        val visitor = tournamentActions(started, "carol", now)
        assertTrue(visitor.registrationClosed)
        assertFalse(visitor.canJoin)

        val creator = tournamentActions(started, "alice", now)
        assertTrue(creator.canCancel)
    }

    // Input: tournaments in progress, completed or cancelled, viewed by the creator, a player and a visitor
    // Expected output: nobody can join, leave or cancel any more
    @Test
    fun closedTournamentsCannotChange() {
        val closed = listOf(TournamentStatus.IN_PROGRESS, TournamentStatus.COMPLETED, TournamentStatus.CANCELLED)
        for (status in closed) {
            for (username in listOf("alice", "bob", "carol")) {
                val actions = tournamentActions(tournament(status = status), username, now)

                assertFalse(actions.canJoin)
                assertFalse(actions.canLeave)
                assertFalse(actions.canCancel)
                assertFalse(actions.registrationClosed)
            }
        }
    }

    // Input: no signed-in user
    // Expected output: not registered and not the creator, so no actions besides joining
    @Test
    fun signedOutUserIsNeitherRegisteredNorCreator() {
        val actions = tournamentActions(tournament(), null, now)

        assertFalse(actions.isRegistered)
        assertFalse(actions.isCreator)
        assertFalse(actions.canLeave)
        assertFalse(actions.canCancel)
    }
}
