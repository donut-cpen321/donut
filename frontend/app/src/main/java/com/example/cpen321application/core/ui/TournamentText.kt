package com.example.cpen321application.core.ui

import com.example.cpen321application.core.data.model.GameTournament
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentPlayer
import com.example.cpen321application.core.data.model.TournamentStatus

// How tournaments are written on screen, shared by the games list, tournament screen and game screen

/**
 * @param round 1, 2 or 3
 * @return "Quarterfinal", "Semifinal" or "Final"
 */
fun roundLabel(round: Int): String = when (round) {
    1 -> "Quarterfinal"
    2 -> "Semifinal"
    else -> "Final"
}

/**
 * The title of a bracket column
 * @param round 1, 2 or 3
 * @return "Quarterfinals", "Semifinals" or "Final"
 */
fun roundTitle(round: Int): String = when (round) {
    1 -> "Quarterfinals"
    2 -> "Semifinals"
    else -> "Final"
}

val TournamentStatus.label: String
    get() = when (this) {
        TournamentStatus.REGISTRATION -> "Registration open"
        TournamentStatus.IN_PROGRESS -> "In progress"
        TournamentStatus.COMPLETED -> "Completed"
        TournamentStatus.CANCELLED -> "Cancelled"
    }

/**
 * Names a team by its players' real names, not their usernames (which are emails for now)
 * @return e.g. "Alice A" in singles, "Alice A & Bob B" in doubles
 */
fun teamName(players: List<TournamentPlayer>): String = players.joinToString(" & ") { it.name }

/**
 * @return e.g. "5/8 players"
 */
fun tournamentPlayerCountText(tournament: Tournament): String =
    "${tournament.players.size}/${tournament.capacity} players"

/**
 * The label of a tournament game
 * @return e.g. "Sunday Smash · Semifinal"
 */
fun gameTournamentText(tournament: GameTournament): String = "${tournament.name} · ${roundLabel(tournament.round)}"
