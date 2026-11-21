package com.donut.app.core.network.dto

import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.MatchGame
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.model.TournamentCreator
import com.donut.app.core.data.model.TournamentMatch
import com.donut.app.core.data.model.TournamentPlayer
import com.donut.app.core.data.model.TournamentStatus
import com.google.gson.annotations.SerializedName
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
    @SerializedName("id", alternate = ["userId", "_id"])
    val id: String? = null,
    val username: String? = null,
    val name: String? = null,
    val rating: Double? = null,
    @SerializedName("photoUrl", alternate = ["avatarUrl", "photo", "picture", "url", "imageUrl", "avatar_url", "photo_url"])
    val photoUrl: String? = null,
    val user: UserMeDto? = null,
)

data class TournamentPlayerDto(
    @SerializedName("id", alternate = ["userId", "_id"])
    val id: String? = null,
    val username: String? = null,
    val name: String? = null,
    val rating: Double? = null,
    val team: Int? = null,
    @SerializedName("photoUrl", alternate = ["avatarUrl", "photo", "picture", "url", "imageUrl", "avatar_url", "photo_url"])
    val photoUrl: String? = null,
    val user: UserMeDto? = null,
    val player: UserMeDto? = null,
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

fun TournamentCreatorDto.toTournamentCreator(): TournamentCreator {
    val effectiveId = user?.id ?: id.orEmpty()
    val effectiveUsername = user?.username ?: username.orEmpty()
    val effectiveName = user?.name ?: name.orEmpty().ifBlank { effectiveUsername }
    val effectiveRating = rating ?: user?.elo?.toDouble() ?: 1500.0
    val effectivePhotoUrl = user?.avatarUrl ?: photoUrl

    return TournamentCreator(
        id = effectiveId,
        username = effectiveUsername,
        name = effectiveName,
        rating = effectiveRating,
        photoUrl = effectivePhotoUrl,
    )
}

fun TournamentPlayerDto.toTournamentPlayer(): TournamentPlayer {
    val effectiveId = user?.id ?: player?.id ?: id.orEmpty()
    val effectiveUsername = user?.username ?: player?.username ?: username.orEmpty()
    val effectiveName = user?.name ?: player?.name ?: name.orEmpty().ifBlank { effectiveUsername }
    val effectiveRating = rating ?: user?.elo?.toDouble() ?: 1500.0
    val effectivePhotoUrl = user?.avatarUrl ?: player?.avatarUrl ?: photoUrl

    return TournamentPlayer(
        id = effectiveId,
        username = effectiveUsername,
        name = effectiveName,
        rating = effectiveRating,
        team = team,
        photoUrl = effectivePhotoUrl,
    )
}

fun TournamentDto.toTournament(): Tournament = Tournament(
    id = id,
    name = name,
    format = Format.valueOf(format),
    status = TournamentStatus.valueOf(status),
    startsAt = Instant.parse(startsAt),
    court = court.toCourt(),
    creator = creator.toTournamentCreator(),
    capacity = capacity,
    players = players.map { it.toTournamentPlayer() },
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
