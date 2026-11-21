package com.donut.app.core.network.dto

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Forecast
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.GameTournament
import com.donut.app.core.data.model.Player
import com.donut.app.core.data.model.Round
import com.donut.app.core.data.model.ScoreProposal
import com.google.gson.annotations.SerializedName
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
    val forecast: ForecastDto?,  // null for a completed or cancelled game; GET /games/history leaves it out
    val tournament: GameTournamentDto?,  // null unless the game is a tournament match
    val scoreProposal: ScoreProposalDto? = null  // null unless a reported score waits for every player to confirm it
)

data class ScoreProposalDto(
    val rounds: List<RoundDto>,
    val proposedById: String,
    val proposedAt: String
)

data class CourtDto(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class PlayerDto(
    @SerializedName("id", alternate = ["userId", "_id"])
    val id: String? = null,
    val username: String? = null,
    val name: String? = null,
    val rating: Double? = null,
    val team: Int? = null,
    val ratingBefore: Double? = null,  // null until the game is completed
    val ratingAfter: Double? = null,
    val acceptedAt: String? = null,  // matched games only: null until the player accepts
    val scoreConfirmedAt: String? = null,  // null until the player agrees with the score waiting
    @SerializedName("photoUrl", alternate = ["avatarUrl", "photo", "picture", "url", "imageUrl", "avatar_url", "photo_url"])
    val photoUrl: String? = null,
    val user: UserMeDto? = null,
    val player: UserMeDto? = null,
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

fun PlayerDto.toPlayer(): Player {
    val effectiveId = user?.id ?: player?.id ?: id.orEmpty()
    val effectiveUsername = user?.username ?: player?.username ?: username.orEmpty()
    val effectiveName = user?.name ?: player?.name ?: name.orEmpty().ifBlank { effectiveUsername }
    val effectiveRating = rating ?: user?.elo?.toDouble() ?: 1500.0
    val effectiveTeam = team ?: 1
    val effectivePhotoUrl = user?.avatarUrl ?: player?.avatarUrl ?: photoUrl

    return Player(
        id = effectiveId,
        username = effectiveUsername,
        name = effectiveName,
        rating = effectiveRating,
        team = effectiveTeam,
        ratingBefore = ratingBefore,
        ratingAfter = ratingAfter,
        acceptedAt = acceptedAt?.let(Instant::parse),
        photoUrl = effectivePhotoUrl,
        scoreConfirmedAt = scoreConfirmedAt?.let(Instant::parse),
    )
}

fun GameDto.toGame(): Game = Game(
    id = id,
    format = Format.valueOf(format),
    status = GameStatus.valueOf(status),
    source = GameSource.valueOf(gameSource),
    startsAt = Instant.parse(startsAt),
    court = court.toCourt(),
    players = players.map { it.toPlayer() },
    rounds = rounds.map { it.toRound() },
    forecast = forecast?.toForecast(),
    tournament = tournament?.let { GameTournament(it.id, it.name, it.round) },
    scoreProposal = scoreProposal?.let {
        ScoreProposal(it.rounds.map { round -> round.toRound() }, it.proposedById, Instant.parse(it.proposedAt))
    }
)

fun ForecastDto.toForecast(): Forecast = Forecast(temperature, description, precipitationChance)

fun RoundDto.toRound(): Round = Round(number, team1Score, team2Score)

fun CourtDto.toCourt(): Court = Court(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude
)
