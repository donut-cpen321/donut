package com.example.cpen321application.feature.matchmaking

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
fun MatchmakingRoute(
    viewModel: MatchmakingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    MatchmakingScreen(uiState = uiState)
}

@Composable
fun MatchmakingScreen(
    uiState: MatchmakingUiState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Matchmaking Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
