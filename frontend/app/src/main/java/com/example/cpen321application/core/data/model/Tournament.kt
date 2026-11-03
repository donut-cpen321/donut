package com.example.cpen321application.core.data.model

import java.time.Instant

// REGISTRATION: players can join until it is full or starts. IN_PROGRESS: the bracket is drawn.
// COMPLETED: the final was played. CANCELLED: the creator cancelled it during registration
enum class TournamentStatus { REGISTRATION, IN_PROGRESS, COMPLETED, CANCELLED }

// Every bracket has 8 teams, so 3 rounds: quarterfinals (1), semifinals (2) and the final (3)
const val BRACKET_TEAMS = 8
const val FINAL_ROUND = 3

data class TournamentPlayer(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double,
    val team: Int?  // the team's seed from 1 to 8, null until the bracket is drawn. Doubles partners share it
)

data class TournamentCreator(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double
)

// The game of a bracket match. Its team 1 is the match's team1, so team1Score belongs to team1
data class MatchGame(
    val id: String,
    val status: GameStatus,
    val startsAt: Instant,
    val court: Court,
    val rounds: List<Round>
)

// One slot of the bracket. The winner of (round, position) plays in (round + 1, position / 2)
data class TournamentMatch(
    val round: Int,  // 1 for a quarterfinal, 2 for a semifinal, 3 for the final
    val position: Int,  // from the top of the bracket, starting at 0
    val team1: Int?,  // seeds, null until the feeder match is decided
    val team2: Int?,
    val winner: Int?,  // null until the score is reported
    val game: MatchGame?  // null until both teams are known
)

data class Tournament(
    val id: String,
    val name: String,
    val format: Format,
    val status: TournamentStatus,
    val startsAt: Instant,
    val court: Court,
    val creator: TournamentCreator,
    val capacity: Int,  // 8 players in singles, 16 in doubles
    val players: List<TournamentPlayer>,  // in the order they joined
    // Only GET /tournaments has them, and they are empty until the bracket is drawn
    val matches: List<TournamentMatch> = emptyList(),
    val winningTeam: Int? = null,  // the champion's seed once completed
    // Only feed tournaments have one, and it is null when OpenWeather could not be reached
    val forecast: Forecast? = null
)

/**
 * @return the players of the team with this seed: one in singles, two in doubles
 */
fun Tournament.playersOnTeam(team: Int): List<TournamentPlayer> = players.filter { it.team == team }

/**
 * @param username the signed-in user's username, null if signed out
 * @return whether that user is registered
 */
fun Tournament.isRegistered(username: String?): Boolean =
    username != null && players.any { it.username == username }
