package com.example.cpen321application.core.network.dto

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
