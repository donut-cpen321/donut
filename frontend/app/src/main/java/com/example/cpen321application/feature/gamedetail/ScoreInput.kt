package com.example.cpen321application.feature.gamedetail

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
        val team1 = round.team1.trim().toIntOrNull()
        val team2 = round.team2.trim().toIntOrNull()
        if (team1 == null || team2 == null || team1 < 0 || team2 < 0) {
            return "Set ${index + 1}: enter a whole number for each team."
        }
        if (team1 == team2) {
            return "Set ${index + 1} is tied. A set needs a winner."
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

/**
 * @return the (team 1, team 2) score of each set. Only call it once scoreError returned null
 */
fun toScores(rounds: List<RoundInput>): List<Pair<Int, Int>> =
    rounds.map { Pair(it.team1.trim().toInt(), it.team2.trim().toInt()) }
