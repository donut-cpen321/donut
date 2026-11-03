package com.example.cpen321application.feature.gamedetail

import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.TEAMS
import com.example.cpen321application.core.data.model.isClosed
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.data.model.teamSize
import java.time.Duration
import java.time.Instant

// Players can't leave a game that starts in less than 30 minutes
private val LEAVE_DEADLINE: Duration = Duration.ofMinutes(30)

/**
 * What the signed-in user can do on a game's screen. Follows the backend's rules,
 * so the screen only offers what the backend accepts.
 */
data class GameActions(
    val isPlayer: Boolean,
    val joinableTeams: List<Int>,  // empty when the user can't join
    val canLeave: Boolean,
    val leaveTooLate: Boolean,  // a player who can't leave because the game starts too soon
    val canChangeTimeOrCourt: Boolean,
    val canReportScore: Boolean,
    val canCancel: Boolean
)

/**
 * Decides which actions the game screen shows
 * @param game the game on screen
 * @param username the signed-in user's username, null if signed out
 * @param now the current time
 */
fun gameActions(game: Game, username: String?, now: Instant): GameActions {
    val isPlayer = username != null && game.players.any { it.username == username }
    val hasStarted = !game.startsAt.isAfter(now)
    val canEdit = isPlayer && !game.isClosed

    // Matched games get their players from matchmaking, not by joining
    val isJoinable = !isPlayer &&
        game.source == GameSource.SCHEDULED &&
        game.status == GameStatus.OPEN &&
        !hasStarted
    val joinableTeams = if (isJoinable) {
        TEAMS.filter { game.playersOnTeam(it).size < game.format.teamSize }
    } else {
        emptyList()
    }

    val beforeLeaveDeadline = Duration.between(now, game.startsAt) >= LEAVE_DEADLINE
    // A tournament game belongs to the bracket: its players can't leave it or cancel it
    val canLeaveOrCancel = canEdit && game.source != GameSource.TOURNAMENT

    return GameActions(
        isPlayer = isPlayer,
        joinableTeams = joinableTeams,
        canLeave = canLeaveOrCancel && beforeLeaveDeadline,
        leaveTooLate = canLeaveOrCancel && !beforeLeaveDeadline,
        // Moving a game that already started would not make sense
        canChangeTimeOrCourt = canEdit && !hasStarted,
        // The score is reported once the game is played
        canReportScore = canEdit && hasStarted,
        canCancel = canLeaveOrCancel
    )
}
