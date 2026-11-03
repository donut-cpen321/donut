package com.example.cpen321application.core.network.dto

import com.example.cpen321application.core.data.model.Format
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.data.model.MatchGame
import com.example.cpen321application.core.data.model.Tournament
import com.example.cpen321application.core.data.model.TournamentCreator
import com.example.cpen321application.core.data.model.TournamentMatch
import com.example.cpen321application.core.data.model.TournamentPlayer
import com.example.cpen321application.core.data.model.TournamentStatus
import java.time.Instant

// Bodies of the /tournaments routes. Like GameDto, enums and dates stay strings until toTournament.

// A tournament as GET /tournaments, /tournaments/feed and /tournaments/me return it
data class TournamentDto(
    val id: String,
    val name: String,
    val format: String,
    val status: String,
    val startsAt: String,
    val capacity: Int,
    val winningTeam: Int?,
    val court: CourtDto,
    val creator: TournamentCreatorDto,
    val players: List<TournamentPlayerDto>,
    // Only GET /tournaments sends it. Gson leaves a missing field null, so it is nullable
    val matches: List<TournamentMatchDto>?,
    val forecast: ForecastDto?  // only GET /tournaments/feed sends it
)

data class TournamentCreatorDto(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double
)

data class TournamentPlayerDto(
    val id: String,
    val username: String,
    val name: String,
    val rating: Double,
    val team: Int?
)

data class TournamentMatchDto(
    val round: Int,
    val position: Int,
    val team1: Int?,
    val team2: Int?,
    val winner: Int?,
    val game: MatchGameDto?
)

data class MatchGameDto(
    val id: String,
    val status: String,
    val startsAt: String,
    val court: CourtDto,
    val rounds: List<RoundDto>
)

data class TournamentsResponseDto(
    val tournaments: List<TournamentDto>
)

data class TournamentResponseDto(
    val tournament: TournamentDto
)

// POST /tournaments returns the new tournament; only its ID is used
data class CreatedTournamentResponseDto(
    val tournament: CreatedTournamentDto
)

data class CreatedTournamentDto(
    val id: String
)

data class CreateTournamentRequestDto(
    val name: String,
    val format: String,
    val startsAt: String,  // ISO 8601 with a time zone
    val courtId: Int
)

// The body of POST /tournaments/join and /tournaments/leave
data class TournamentIdRequestDto(
    val tournamentId: String
)

fun TournamentDto.toTournament(): Tournament = Tournament(
    id = id,
    name = name,
    format = Format.valueOf(format),
    status = TournamentStatus.valueOf(status),
    startsAt = Instant.parse(startsAt),
    court = court.toCourt(),
    creator = TournamentCreator(creator.id, creator.username, creator.name, creator.rating),
    capacity = capacity,
    players = players.map { TournamentPlayer(it.id, it.username, it.name, it.rating, it.team) },
    matches = matches.orEmpty().map { it.toTournamentMatch() },
    winningTeam = winningTeam,
    forecast = forecast?.toForecast()
)

fun TournamentMatchDto.toTournamentMatch(): TournamentMatch = TournamentMatch(
    round = round,
    position = position,
    team1 = team1,
    team2 = team2,
    winner = winner,
    game = game?.toMatchGame()
)

fun MatchGameDto.toMatchGame(): MatchGame = MatchGame(
    id = id,
    status = GameStatus.valueOf(status),
    startsAt = Instant.parse(startsAt),
    court = court.toCourt(),
    rounds = rounds.map { it.toRound() }
)
