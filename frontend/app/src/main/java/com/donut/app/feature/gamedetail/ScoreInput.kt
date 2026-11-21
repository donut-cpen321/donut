package com.donut.app.feature.gamedetail

// One set in the score form. The scores stay text so a half-typed score can be shown as typed
data class RoundInput(
    val team1: String = "",
    val team2: String = ""
)

/**
 * Checks a final score with the same rules as the backend, so the user sees what to fix before submitting
 * @param rounds the sets in order
 * @return the message to show, or null if the score can be submitted
 */
fun scoreError(rounds: List<RoundInput>): String? {
    if (rounds.isEmpty()) {
        return "Add at least one set."
    }

    var team1Wins = 0
    rounds.forEachIndexed { index, round ->
        val team1 = parseNonNegativeScore(round.team1)
        val team2 = parseNonNegativeScore(round.team2)
        if (team1 == null || team2 == null) {
            return "Set ${index + 1}: enter a whole number for each team."
        }
        if (team1 == team2) {
            return "Set ${index + 1} is tied. A set needs a winner."
        }
        if (!isLegalSet(team1, team2)) {
            return "Set ${index + 1}: past 6 games, a set is won by 2 games, or 7–6."
        }
        if (team1 > team2) {
            team1Wins++
        }
    }

    // The winner is the team that won more sets
    if (team1Wins * 2 == rounds.size) {
        return "Both teams won the same number of sets, so there is no winner."
    }

    return null
}

private fun parseNonNegativeScore(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

// A set is won at 6 games. Past 6, it is won by exactly 2 games, or 7–6 after a tiebreak
private const val SET_GAMES = 6

// Same rule as the backend: any score up to 6 games, and past 6 a 2-game margin or 7–6. The set isn't tied
private fun isLegalSet(team1: Int, team2: Int): Boolean {
    val winner = maxOf(team1, team2)
    val loser = minOf(team1, team2)
    val wonByTwo = winner - loser == 2
    val tiebreak = winner == SET_GAMES + 1 && loser == SET_GAMES
    return winner <= SET_GAMES || wonByTwo || tiebreak
}

/**
 * @return the (team 1, team 2) score of each set. Only call it once scoreError returned null
 */
fun toScores(rounds: List<RoundInput>): List<Pair<Int, Int>> =
    rounds.map { Pair(it.team1.trim().toInt(), it.team2.trim().toInt()) }
