package com.example.cpen321application.core.data.model

import java.time.Instant

enum class Format { SINGLES, DOUBLES }

// OPEN: a scheduled game with free spots, or a matched game waiting for every player to accept
// BOOKED: every spot filled. COMPLETED: final score reported. CANCELLED: acceptance deadline missed
enum class GameStatus { OPEN, BOOKED, COMPLETED, CANCELLED }

// SCHEDULED: created by a player and shown on the feed. MATCHED: proposed by matchmaking
enum class GameSource { SCHEDULED, MATCHED }

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
    val team: Int
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
    val forecast: Forecast? = null
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
 * A completed or cancelled game can no longer be joined, left or changed
 */
val Game.isClosed: Boolean
    get() = status == GameStatus.COMPLETED || status == GameStatus.CANCELLED
