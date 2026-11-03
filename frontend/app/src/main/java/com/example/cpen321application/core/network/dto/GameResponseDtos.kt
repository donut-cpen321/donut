package com.example.cpen321application.core.network.dto

// Response bodies of the /games and /courts routes

data class GamesResponseDto(
    val games: List<GameDto>
)

data class GameResponseDto(
    val game: GameDto
)

// POST /games returns the new game without its court, players and rounds; only its ID is used
data class CreatedGameResponseDto(
    val game: CreatedGameDto
)

data class CreatedGameDto(
    val id: String
)

// POST /games/invite: false when the friend has no device to notify
data class InviteFriendResponseDto(
    val notified: Boolean
)

data class CourtsResponseDto(
    val courts: List<CourtDto>
)
