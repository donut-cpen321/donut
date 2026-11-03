package com.example.cpen321application.feature.gamedetail

import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.TEAMS
import com.example.cpen321application.core.data.model.isClosed
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.data.model.teamSize
import com.example.cpen321application.core.network.dto.FriendDto
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
    val canCancel: Boolean,
    val canInvite: Boolean = false  // a player of a game that friends can still join
)

/**
 * The friends who can be invited to a game: those not already playing in it
 * @param friends the signed-in user's friends
 * @param game the game to invite them to
 */
fun invitableFriends(friends: List<FriendDto>, game: Game): List<FriendDto> {
    val playerIds = game.players.map { it.id }.toSet()
    return friends.filter { it.userId !in playerIds }
}

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
    val isOpenToJoin = game.source == GameSource.SCHEDULED &&
        game.status == GameStatus.OPEN &&
        !hasStarted
    val joinableTeams = if (!isPlayer && isOpenToJoin) {
        TEAMS.filter { game.playersOnTeam(it).size < game.format.teamSize }
    } else {
        emptyList()
    }

    val beforeLeaveDeadline = Duration.between(now, game.startsAt) >= LEAVE_DEADLINE
    // A tournament game belongs to the bracket: its players can't leave it or cancel it
    val canLeaveOrCancel = canEdit && game.source != GameSource.TOURNAMENT
    // Matched players keep the players, time and court matchmaking picked
    val canLeaveOrMove = canEdit && game.source != GameSource.MATCHED

    return GameActions(
        isPlayer = isPlayer,
        joinableTeams = joinableTeams,
        canLeave = canLeaveOrCancel && canLeaveOrMove && beforeLeaveDeadline,
        leaveTooLate = canLeaveOrCancel && canLeaveOrMove && !beforeLeaveDeadline,
        // Moving a game that already started would not make sense
        canChangeTimeOrCourt = canLeaveOrMove && !hasStarted,
        // The score is reported once the game is played
        canReportScore = canEdit && hasStarted,
        canCancel = canLeaveOrCancel,
        // An invited friend joins like anyone else, so only games they could join
        canInvite = isPlayer && isOpenToJoin
    )
}
