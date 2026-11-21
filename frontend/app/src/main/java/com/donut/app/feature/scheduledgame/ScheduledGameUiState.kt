package com.donut.app.feature.scheduledgame

import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.Tournament
import com.donut.app.core.data.model.isRegistered

enum class GamesTab(val title: String) {
    FEED("Feed"),
    MY_GAMES("My games")
}

data class ScheduledGameUiState(
    val selectedTab: GamesTab = GamesTab.FEED,
    val feed: List<Game> = emptyList(),
    val myGames: List<Game> = emptyList(),
    val tournamentFeed: List<Tournament> = emptyList(),  // tournaments open for registration
    val myTournaments: List<Tournament> = emptyList(),
    val username: String? = null,  // the signed-in user, to mark the games they play in
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    // The Feed tab: games to join and tournaments to register for, soonest first. The user's own are on My games
    val feedItems: List<FeedItem>
        get() = mergeByStartTime(
            feed.filterNot { game -> game.players.any { it.username == username } },
            tournamentFeed.filterNot { it.isRegistered(username) }
        )

    // The My games tab: the user's games, including tournament matches, and their tournaments
    val myItems: List<FeedItem>
        get() = mergeByStartTime(myGames, myTournaments)
}
