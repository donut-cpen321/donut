package com.donut.app.core.network.dto

/**
 * @param format SINGLES, DOUBLES or EITHER
 */
data class JoinQueueRequestDto(
    val format: String,
    val latitude: Double,
    val longitude: Double
)

data class QueueEntryDto(
    val format: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: String
)

data class QueueEntryResponseDto(
    val request: QueueEntryDto
)

data class PointDto(
    val latitude: Double,
    val longitude: Double
)

/**
 * @param center where the player is searching from
 * @param players the other queued players within 10 km, rounded to ~100 m
 */
data class NearbyPlayersDto(
    val center: PointDto,
    val players: List<PointDto>
)
