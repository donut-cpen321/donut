package com.example.cpen321application.feature.tournamentdetail

import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentStatus
import com.example.cpen321application.core.data.model.isRegistered
import java.time.Instant

/**
 * What the signed-in user can do on a tournament's screen. Follows the backend's rules,
 * so the screen only offers what the backend accepts.
 */
data class TournamentActions(
    val isRegistered: Boolean,
    val isCreator: Boolean,
    val registrationClosed: Boolean,  // still open for registration, but it started before it filled
    val canJoin: Boolean,
    val canLeave: Boolean,
    val canCancel: Boolean
)

/**
 * Decides which actions the tournament screen shows
 * @param tournament the tournament on screen
 * @param username the signed-in user's username, null if signed out
 * @param now the current time
 */
fun tournamentActions(tournament: Tournament, username: String?, now: Instant): TournamentActions {
    val isRegistered = tournament.isRegistered(username)
    val isCreator = username != null && tournament.creator.username == username
    val isOpen = tournament.status == TournamentStatus.REGISTRATION
    val hasStarted = !tournament.startsAt.isAfter(now)
    val hasSpot = tournament.players.size < tournament.capacity

    return TournamentActions(
        isRegistered = isRegistered,
        isCreator = isCreator,
        registrationClosed = isOpen && hasStarted,
        canJoin = isOpen && !hasStarted && !isRegistered && hasSpot,
        // The creator cancels the tournament instead
        canLeave = isOpen && isRegistered && !isCreator,
        canCancel = isOpen && isCreator
    )
}
