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

data class CourtsResponseDto(
    val courts: List<CourtDto>
)
