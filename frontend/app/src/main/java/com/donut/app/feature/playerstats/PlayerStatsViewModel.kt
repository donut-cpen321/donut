package com.donut.app.feature.playerstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donut.app.core.data.repository.StatsRepository
import com.donut.app.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads a player's stats when created
 * @param repository fetches the stats as the signed-in user
 * @param userId the player whose stats are shown, null for the signed-in user
 */
class PlayerStatsViewModel(
    private val repository: StatsRepository,
    private val userId: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerStatsUiState())
    val uiState: StateFlow<PlayerStatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Loads the stats again; does nothing while a load is in progress */
    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getStats(userId)
                .onSuccess { stats ->
                    _uiState.update { it.copy(isLoading = false, stats = stats) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                }
        }
    }
}
