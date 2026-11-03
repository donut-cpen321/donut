package com.example.cpen321application.feature.tournamentdetail

import com.example.cpen321application.core.data.model.BRACKET_TEAMS
import com.example.cpen321application.core.data.model.FINAL_ROUND
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentMatch
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.ui.roundTitle
import com.example.cpen321application.core.ui.teamName

// Shown for a team that is not known yet, until its feeder match is decided
const val TBD_TEAM_NAME = "TBD"

/**
 * One team of a bracket match, as the bracket shows it
 * @property seed the team's seed from 1 to 8, null until it is known
 * @property name its players' names, e.g. "Alice A & Bob B", or TBD_TEAM_NAME
 * @property scores the games it won in each set, empty until the score is reported
 * @property isWinner whether it won the match
 */
data class BracketSlot(
    val seed: Int?,
    val name: String,
    val scores: List<Int>,
    val isWinner: Boolean
)

/**
 * A match of the bracket
 * @property gameId the match's game, null until both teams are known. Tapping the match opens it
 */
data class BracketMatch(
    val round: Int,
    val position: Int,
    val team1: BracketSlot,
    val team2: BracketSlot,
    val gameId: String?
)

/**
 * One round of the bracket, from top to bottom
 * @property title "Quarterfinals", "Semifinals" or "Final"
 */
data class BracketColumn(
    val round: Int,
    val title: String,
    val matches: List<BracketMatch>
)

/**
 * Builds the bracket the tournament screen shows: 4 quarterfinals, 2 semifinals and the final.
 * A match the backend has not sent (before the draw) shows as TBD against TBD.
 */
fun bracketColumns(tournament: Tournament): List<BracketColumn> = (1..FINAL_ROUND).map { round ->
    // 8 teams play 4 quarterfinals, then 2 semifinals, then 1 final
    val matchCount = BRACKET_TEAMS shr round
    BracketColumn(
        round = round,
        title = roundTitle(round),
        matches = (0 until matchCount).map { position ->
            val match = tournament.matches.firstOrNull { it.round == round && it.position == position }
            bracketMatch(tournament, round, position, match)
        }
    )
}

private fun bracketMatch(tournament: Tournament, round: Int, position: Int, match: TournamentMatch?): BracketMatch {
    val rounds = match?.game?.rounds.orEmpty()
    return BracketMatch(
        round = round,
        position = position,
        team1 = bracketSlot(tournament, match?.team1, match?.winner, rounds.map { it.team1Score }),
        team2 = bracketSlot(tournament, match?.team2, match?.winner, rounds.map { it.team2Score }),
        gameId = match?.game?.id
    )
}

private fun bracketSlot(tournament: Tournament, seed: Int?, winner: Int?, scores: List<Int>): BracketSlot {
    val name = if (seed == null) {
        TBD_TEAM_NAME
    } else {
        teamName(tournament.playersOnTeam(seed)).ifEmpty { "Team $seed" }
    }
    return BracketSlot(
        seed = seed,
        name = name,
        scores = scores,
        isWinner = seed != null && seed == winner
    )
}
