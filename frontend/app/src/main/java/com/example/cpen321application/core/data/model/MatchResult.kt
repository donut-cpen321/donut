package com.example.cpen321application.core.data.model

import kotlin.math.roundToInt

enum class MatchOutcome { WIN, LOSS }

/**
 * A completed game as one of its players sees it: who they played with and against, and how it went
 * @property game the completed game
 * @property me the player whose point of view this is
 * @property outcome whether my team won, null if the score has no winner
 * @property partners my teammates, empty in singles
 * @property opponents the players on the other team
 * @property myScores the (my team, other team) score of each round, in order
 * @property ratingChange how much my rating changed, rounded, null if the backend did not record it
 */
data class MatchResult(
    val game: Game,
    val me: Player,
    val outcome: MatchOutcome?,
    val partners: List<Player>,
    val opponents: List<Player>,
    val myScores: List<Pair<Int, Int>>,
    val ratingChange: Int?
)

/**
 * @param username the username of the player whose point of view to take
 * @return the game from that player's point of view, or null if they did not play in it
 */
fun Game.resultFor(username: String?): MatchResult? {
    val me = players.firstOrNull { it.username == username } ?: return null
    val otherTeam = if (me.team == 1) 2 else 1
    val winner = winningTeam

    return MatchResult(
        game = this,
        me = me,
        outcome = winner?.let { if (it == me.team) MatchOutcome.WIN else MatchOutcome.LOSS },
        partners = playersOnTeam(me.team).filter { it.id != me.id },
        opponents = playersOnTeam(otherTeam),
        myScores = rounds.map { round ->
            if (me.team == 1) round.team1Score to round.team2Score else round.team2Score to round.team1Score
        },
        ratingChange = ratingChange(me)
    )
}

private fun ratingChange(player: Player): Int? {
    val before = player.ratingBefore ?: return null
    val after = player.ratingAfter ?: return null
    return after.roundToInt() - before.roundToInt()
}
