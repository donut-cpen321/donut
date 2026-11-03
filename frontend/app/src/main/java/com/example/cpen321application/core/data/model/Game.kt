package com.example.cpen321application.core.data.model

import java.time.Instant

enum class Format { SINGLES, DOUBLES }

// OPEN: a scheduled game with free spots, or a matched game waiting for every player to accept
// BOOKED: every spot filled. COMPLETED: final score reported. CANCELLED: acceptance deadline missed
enum class GameStatus { OPEN, BOOKED, COMPLETED, CANCELLED }

// SCHEDULED: created by a player and shown on the feed. MATCHED: proposed by matchmaking.
// TOURNAMENT: a match of a tournament bracket, created by the backend when both teams are known
enum class GameSource { SCHEDULED, MATCHED, TOURNAMENT }

// The two teams of a game. The creator of a scheduled game is on team 1
val TEAMS = listOf(1, 2)

data class Court(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class Player(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double,
    val team: Int,
    // The player's rating before and after the game, null until its score is reported
    val ratingBefore: Double? = null,
    val ratingAfter: Double? = null,
    // Matched games only: null until the player accepts
    val acceptedAt: Instant? = null
)

// One round of a game (a set in tennis)
data class Round(
    val number: Int,
    val team1Score: Int,
    val team2Score: Int
)

// The weather at the court around the game's start time
data class Forecast(
    val temperature: Double,  // degrees Celsius
    val description: String,
    val precipitationChance: Double  // from 0 to 1
)

// The tournament a bracket game belongs to. round is 1 for a quarterfinal, 2 for a semifinal, 3 for the final
data class GameTournament(
    val id: String,
    val name: String,
    val round: Int
)

data class Game(
    val id: String,
    val format: Format,
    val status: GameStatus,
    val source: GameSource,
    val startsAt: Instant,
    val court: Court,
    val players: List<Player>,
    val rounds: List<Round>,
    // Only feed games have one, and it is null when OpenWeather could not be reached
    val forecast: Forecast? = null,
    // Only tournament games have one
    val tournament: GameTournament? = null
)

/**
 * The number of players on each team: 1 in singles, 2 in doubles
 */
val Format.teamSize: Int
    get() = when (this) {
        Format.SINGLES -> 1
        Format.DOUBLES -> 2
    }

/**
 * @return the players on the given team, 1 or 2
 */
fun Game.playersOnTeam(team: Int): List<Player> = players.filter { it.team == team }

/**
 * The team that won more rounds, or null if the game has no score yet or its rounds are split evenly
 */
val Game.winningTeam: Int?
    get() {
        if (rounds.isEmpty()) return null
        val team1Wins = rounds.count { it.team1Score > it.team2Score }
        val team2Wins = rounds.count { it.team2Score > it.team1Score }
        return when {
            team1Wins > team2Wins -> 1
            team2Wins > team1Wins -> 2
            else -> null
        }
    }

/**
 * A completed or cancelled game can no longer be joined, left or changed
 */
val Game.isClosed: Boolean
    get() = status == GameStatus.COMPLETED || status == GameStatus.CANCELLED
