package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.GameStatus
import com.example.cpen321application.core.ui.forecastText
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.playerCountText

@Composable
fun ScheduledGameRoute(
    viewModel: ScheduledGameViewModel,
    onGameClick: (gameId: String) -> Unit,
    onCreateGame: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the tab is shown, including coming back from a game's screen
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ScheduledGameScreen(
        uiState = uiState,
        onTabSelected = viewModel::selectTab,
        onRefresh = viewModel::load,
        onGameClick = onGameClick,
        onCreateGame = onCreateGame
    )
}

@Composable
fun ScheduledGameScreen(
    uiState: ScheduledGameUiState,
    onTabSelected: (GamesTab) -> Unit,
    onRefresh: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    onCreateGame: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Games",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            Button(onClick = onCreateGame) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Schedule")
            }
        }

        TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
            GamesTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.title) }
                )
            }
        }

        if (uiState.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh) { Text("Retry") }
            }
        }

        val games = when (uiState.selectedTab) {
            GamesTab.FEED -> uiState.feed
            GamesTab.MY_GAMES -> uiState.myGames
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && games.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                games.isEmpty() -> Text(
                    text = when (uiState.selectedTab) {
                        GamesTab.FEED -> "No open games right now. Schedule one!"
                        GamesTab.MY_GAMES -> "You have no upcoming games."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(games, key = { it.id }) { game ->
                        GameCard(
                            game = game,
                            isPlaying = game.players.any { it.username == uiState.username },
                            onClick = { onGameClick(game.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatStartsAt(game.startsAt),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (game.status != GameStatus.OPEN) {
                    Text(
                        text = game.status.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(text = game.court.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${game.format.label} · ${playerCountText(game)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (game.forecast != null) {
                Text(
                    text = forecastText(game.forecast),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isPlaying) {
                Text(
                    text = "You're playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
