package com.example.cpen321application.core.network.dto

// Request bodies of the /games routes. Dates are ISO 8601 strings with a time zone

data class CreateGameRequestDto(
    val format: String,
    val startsAt: String,
    val courtId: Int,
    val gameSource: String
)

data class JoinGameRequestDto(
    val gameId: String,
    val team: Int
)

data class LeaveGameRequestDto(
    val gameId: String
)

data class AcceptMatchRequestDto(
    val gameId: String
)

// userId is the friend to invite
data class InviteFriendRequestDto(
    val gameId: String,
    val userId: String
)

data class ChangeGameTimeRequestDto(
    val gameId: String,
    val startsAt: String
)

data class ChangeGameLocationRequestDto(
    val gameId: String,
    val courtId: Int
)

data class ReportScoreRequestDto(
    val gameId: String,
    val rounds: List<List<Int>>  // [team 1 score, team 2 score] of each round, in order
)
