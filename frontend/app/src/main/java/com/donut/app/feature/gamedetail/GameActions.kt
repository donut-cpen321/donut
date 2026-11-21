package com.donut.app.feature.gamedetail

import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.data.model.TEAMS
import com.donut.app.core.data.model.isClosed
import com.donut.app.core.data.model.playersOnTeam
import com.donut.app.core.data.model.teamSize
import com.donut.app.core.network.dto.FriendDto
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
    val canReportScore: Boolean,  // also to correct the score waiting for confirmation
    val canStartNow: Boolean,  // a player of a booked game, which only starts when a player starts it
    val canCancel: Boolean,
    val canInvite: Boolean = false,  // a player of a game that friends can still join
    val canConfirmScore: Boolean = false  // a player who hasn't agreed with the score waiting yet
)

/**
 * The players who still have to agree with the score waiting, in team order; empty when no score is waiting
 */
fun playersYetToConfirm(game: Game): List<Player> =
    if (game.scoreProposal == null) emptyList() else game.players.filter { it.scoreConfirmedAt == null }

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
 * When gameActions next gives a different answer just because time passed: the leave deadline, or the start
 * time, when joining closes. The screen recomputes its actions then
 * @param game the game on screen
 * @param now the current time
 * @return the next such moment, or null if there is none
 */
fun nextActionsChange(game: Game, now: Instant): Instant? {
    if (game.isClosed || game.status == GameStatus.IN_PROGRESS) {
        return null
    }
    return listOf(game.startsAt.minus(LEAVE_DEADLINE), game.startsAt).firstOrNull { it.isAfter(now) }
}

/**
 * Decides which actions the game screen shows
 * @param game the game on screen
 * @param username the signed-in user's username, null if signed out
 * @param now the current time
 */
fun gameActions(game: Game, username: String?, now: Instant): GameActions {
    val isPlayer = username != null && game.players.any { it.username == username }
    // A game only starts when a player taps Start, never on its own at its start time
    val hasStarted = game.status == GameStatus.IN_PROGRESS
    val canEdit = isPlayer && !game.isClosed

    // Matched games get their players from matchmaking, not by joining. Joining closes at the start time
    val isOpenToJoin = game.source == GameSource.SCHEDULED &&
        game.status == GameStatus.OPEN &&
        game.startsAt.isAfter(now)
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
        // The score is reported once the game was started and played
        canReportScore = canEdit && hasStarted,
        // Every player has to agree with the reported score before the game counts
        canConfirmScore = canEdit && hasStarted && playersYetToConfirm(game).any { it.username == username },
        // Players can start a booked game, early or late: every spot is filled and every player accepted
        canStartNow = canEdit && game.status == GameStatus.BOOKED,
        canCancel = canLeaveOrCancel,
        // An invited friend joins like anyone else, so only games they could join
        canInvite = isPlayer && isOpenToJoin
    )
}
