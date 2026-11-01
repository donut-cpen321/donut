package com.example.cpen321application.feature.scheduledgame

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScheduledGameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduledGameUiState())
    val uiState: StateFlow<ScheduledGameUiState> = _uiState.asStateFlow()
}
