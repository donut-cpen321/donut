package com.example.cpen321application.core.network.dto

import com.example.cpen321application.core.data.model.Court
import com.example.cpen321application.core.data.model.Forecast
import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameSource
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.GameTournament
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.data.model.Round
import java.time.Instant

// A game with its court, players and rounds, as GET /games, /games/feed, /games/me and /games/history return it.
// Enums and dates stay strings here because Gson can't parse java.time or reject unknown enum values.
data class GameDto(
    val id: String,
    val format: String,
    val status: String,
    val gameSource: String,
    val startsAt: String,
    val court: CourtDto,
    val players: List<PlayerDto>,
    val rounds: List<RoundDto>,
    val forecast: ForecastDto?,
    val tournament: GameTournamentDto?  // null unless the game is a tournament match
)

data class CourtDto(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class PlayerDto(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double,
    val team: Int,
    val ratingBefore: Double? = null,  // null until the game is completed
    val ratingAfter: Double? = null,
    val acceptedAt: String? = null  // matched games only: null until the player accepts
)

data class RoundDto(
    val number: Int,
    val team1Score: Int,
    val team2Score: Int
)

data class GameTournamentDto(
    val id: String,
    val name: String,
    val round: Int
)

data class ForecastDto(
    val temperature: Double,
    val description: String,
    val precipitationChance: Double
)

fun GameDto.toGame(): Game = Game(
    id = id,
    format = Format.valueOf(format),
    status = GameStatus.valueOf(status),
    source = GameSource.valueOf(gameSource),
    startsAt = Instant.parse(startsAt),
    court = court.toCourt(),
    players = players.map {
        Player(it.id, it.username, it.name, it.rating, it.team, it.ratingBefore, it.ratingAfter, it.acceptedAt?.let(Instant::parse))
    },
    rounds = rounds.map { it.toRound() },
    forecast = forecast?.toForecast(),
    tournament = tournament?.let { GameTournament(it.id, it.name, it.round) }
)

fun ForecastDto.toForecast(): Forecast = Forecast(temperature, description, precipitationChance)

fun RoundDto.toRound(): Round = Round(number, team1Score, team2Score)

fun CourtDto.toCourt(): Court = Court(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude
)
