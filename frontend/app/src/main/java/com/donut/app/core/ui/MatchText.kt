package com.donut.app.core.ui

import com.donut.app.core.data.model.MatchResult
import com.donut.app.core.data.model.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// How completed games are written on screen, shared by the match history and the match summary

/**
 * @return the players' names joined, e.g. "Ana Lee & Ben Park", or "Unknown" for an empty team
 */
fun playerNames(players: List<Player>): String =
    players.joinToString(" & ") { it.name }.ifEmpty { "Unknown" }

/**
 * @param scores the (my team, other team) score of each round
 * @return e.g. "6–4, 3–6, 7–5"
 */
fun scoresText(scores: List<Pair<Int, Int>>): String =
    scores.joinToString(", ") { (mine, theirs) -> "$mine–$theirs" }

/**
 * @return e.g. "+16" or "−12", with "±0" for no change
 */
fun ratingChangeText(change: Int): String = when {
    change > 0 -> "+$change"
    change < 0 -> "−${-change}"
    else -> "±0"
}

/**
 * @return "6–4  7–5", the signed-in user's games first, sets two spaces apart
 */
fun setsText(scores: List<Pair<Int, Int>>): String =
    scores.joinToString("  ") { (mine, theirs) -> "$mine–$theirs" }

/**
 * The second line of a match history row
 * @return e.g. "6–4  7–5 · Kitsilano Beach Park · Nov 12"
 */
fun historyDetailText(match: MatchResult, zone: ZoneId = ZoneId.systemDefault()): String =
    listOf(setsText(match.myScores), match.game.court.name, shortDateFormatter.format(match.game.startsAt.atZone(zone)))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")
