package com.donut.app.feature.scheduledgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donut.app.core.data.repository.GameListUpdate
import com.donut.app.core.data.repository.GameListUpdates
import com.donut.app.core.data.repository.GamesRepository
import com.donut.app.core.data.repository.TournamentsRepository
import com.donut.app.core.data.repository.reconnecting
import com.donut.app.core.network.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Games tab: the feed of games and tournaments to join, and the user's own
 * @param gamesRepository the /games routes
 * @param tournamentsRepository the /tournaments routes
 * @param currentUsername the signed-in user's username, null if signed out
 * @param gameListUpdates the /games/feed/events stream, which pushes both lists
 */
class ScheduledGameViewModel(
    private val gamesRepository: GamesRepository,
    private val tournamentsRepository: TournamentsRepository,
    private val currentUsername: () -> String?,
    private val gameListUpdates: GameListUpdates
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduledGameUiState(isLoading = true))
    val uiState: StateFlow<ScheduledGameUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun selectTab(tab: GamesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Shows the feed and the user's games and tournaments as the backend pushes them, until the caller stops
     * collecting: anything anyone creates, joins, leaves, starts, scores or cancels shows up without a refresh.
     * Reconnects after the connection drops; the backend sends both lists on every connect, so changes made
     * while disconnected (or on another screen) aren't missed
     */
    suspend fun followLists() {
        gameListUpdates.updates()
            .reconnecting { error ->
                // The backend refused the stream: the tab keeps the lists it last showed
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
            }
            .collect { update ->
                _uiState.update {
                    when (update) {
                        is GameListUpdate.Feed -> it.copy(feed = update.games, tournamentFeed = update.tournaments)
                        is GameListUpdate.Mine -> it.copy(myGames = update.games, myTournaments = update.tournaments)
                    }.copy(isLoading = false, errorMessage = null, username = currentUsername())
                }
            }
    }

    /**
     * Loads the feeds and the user's games and tournaments once, for Refresh and Retry
     */
    fun load() {
        // Only the latest load updates the screen
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, username = currentUsername()) }

            val feed = async { gamesRepository.getFeed() }
            val myGames = async { gamesRepository.getMyGames() }
            val tournamentFeed = async { tournamentsRepository.getFeed() }
            val myTournaments = async { tournamentsRepository.getMyTournaments() }
            val feedResult = feed.await()
            val myGamesResult = myGames.await()
            val tournamentFeedResult = tournamentFeed.await()
            val myTournamentsResult = myTournaments.await()

            // Keep showing the last lists that loaded if a request fails
            val error = listOf(feedResult, myGamesResult, tournamentFeedResult, myTournamentsResult)
                .firstNotNullOfOrNull { it.exceptionOrNull() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    feed = feedResult.getOrDefault(it.feed),
                    myGames = myGamesResult.getOrDefault(it.myGames),
                    tournamentFeed = tournamentFeedResult.getOrDefault(it.tournamentFeed),
                    myTournaments = myTournamentsResult.getOrDefault(it.myTournaments),
                    errorMessage = error?.toUserMessage()
                )
            }
        }
    }
}
