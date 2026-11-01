package com.example.cpen321application.feature.scheduledgame

import com.example.cpen321application.core.data.model.Game

enum class GamesTab(val title: String) {
    FEED("Feed"),
    MY_GAMES("My games")
}

data class ScheduledGameUiState(
    val selectedTab: GamesTab = GamesTab.FEED,
    val feed: List<Game> = emptyList(),
    val myGames: List<Game> = emptyList(),
    val username: String? = null,  // the signed-in user, to mark the games they play in
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
