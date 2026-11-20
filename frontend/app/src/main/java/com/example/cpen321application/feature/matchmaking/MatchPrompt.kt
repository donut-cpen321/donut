package com.example.cpen321application.feature.matchmaking

import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.Player
import kotlin.math.roundToInt

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
        isBooked = game.status == GameStatus.BOOKED || game.status == GameStatus.IN_PROGRESS
    )
}

// Under 45 minutes away a game starts "In 30 min"; up to 90 it is "An hour from now"
private const val MINUTES_FOR_AN_HOUR = 45L
private const val MINUTES_PAST_AN_HOUR = 90L
private const val MINUTES_PER_HOUR = 60L

/**
 * The match sheet's title: "It's a match!", then "You're in!" once the user accepted, then "Game on!"
 */
fun matchTitle(prompt: MatchPrompt): String = when {
    prompt.isBooked -> "Game on!"
    prompt.hasAccepted -> "You're in!"
    else -> "It's a match!"
}

/**
 * The line under the title, e.g. "Jonah (1659) wants a singles game too."
 * @param format the game's format as shown, e.g. "Singles"
 */
fun matchSubtitle(prompt: MatchPrompt, format: String): String {
    val opponents = prompt.opponents
    return when {
        prompt.isBooked -> "Everyone's in. See you on court!"
        prompt.hasAccepted -> "Waiting for ${firstNames(prompt.waitingFor)} to say yes."
        opponents.size == 1 ->
            "${firstNames(opponents)} (${opponents.first().rating.roundToInt()}) wants a ${format.lowercase()} game too."
        else -> "${firstNames(opponents)} want a ${format.lowercase()} game too."
    }
}

private fun firstNames(players: List<Player>): String =
    players.joinToString(" & ") { it.name.substringBefore(" ") }

/**
 * How soon the game starts, under its time in the match sheet
 * @param minutes from now until the start
 * @return "Starting now", "In 30 min", "An hour from now" or "In about 2 hours"
 */
fun startsInText(minutes: Long): String = when {
    minutes < 1 -> "Starting now"
    minutes < MINUTES_FOR_AN_HOUR -> "In $minutes min"
    minutes < MINUTES_PAST_AN_HOUR -> "An hour from now"
    else -> "In about ${(minutes + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR} hours"
}
