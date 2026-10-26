package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScheduledGameRoute(
    viewModel: ScheduledGameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ScheduledGameScreen(uiState = uiState)
}

@Composable
fun ScheduledGameScreen(
    uiState: ScheduledGameUiState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Scheduled Games Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
