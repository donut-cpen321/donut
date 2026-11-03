package com.example.cpen321application.feature.matchmaking

import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Player

/**
 * What the match pop-up tells the signed-in user about their matched game
 */
data class MatchPrompt(
    val partners: List<Player>,  // the signed-in user's teammate in doubles
    val opponents: List<Player>,  // the other team
    val hasAccepted: Boolean,  // the signed-in user
    val waitingFor: List<Player>,  // other players who haven't accepted
    val isBooked: Boolean
)

/**
 * @param game the matched game
 * @param username the signed-in user's username
 */
fun matchPrompt(game: Game, username: String?): MatchPrompt {
    val me = game.players.find { it.username == username }
    val others = game.players.filter { it.username != username }
    return MatchPrompt(
        partners = others.filter { it.team == me?.team },
        opponents = others.filter { it.team != me?.team },
        hasAccepted = me?.acceptedAt != null,
        waitingFor = others.filter { it.acceptedAt == null },
        isBooked = game.status == GameStatus.BOOKED
    )
}
