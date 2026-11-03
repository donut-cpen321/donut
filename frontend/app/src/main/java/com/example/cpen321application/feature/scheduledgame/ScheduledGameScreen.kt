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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.isRegistered

@Composable
fun ScheduledGameRoute(
    viewModel: ScheduledGameViewModel,
    onGameClick: (gameId: String) -> Unit,
    onTournamentClick: (tournamentId: String) -> Unit,
    onCreateGame: () -> Unit,
    onCreateTournament: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the tab is shown, including coming back from a game's or tournament's screen
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ScheduledGameScreen(
        uiState = uiState,
        onTabSelected = viewModel::selectTab,
        onRefresh = viewModel::load,
        onGameClick = onGameClick,
        onTournamentClick = onTournamentClick,
        onCreateGame = onCreateGame,
        onCreateTournament = onCreateTournament
    )
}

@Composable
fun ScheduledGameScreen(
    uiState: ScheduledGameUiState,
    onTabSelected: (GamesTab) -> Unit,
    onRefresh: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    onTournamentClick: (tournamentId: String) -> Unit,
    onCreateGame: () -> Unit,
    onCreateTournament: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GamesHeader(
            isLoading = uiState.isLoading,
            onRefresh = onRefresh,
            onCreateGame = onCreateGame,
            onCreateTournament = onCreateTournament
        )

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

        FeedList(uiState = uiState, onGameClick = onGameClick, onTournamentClick = onTournamentClick)
    }
}

@Composable
private fun GamesHeader(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onCreateGame: () -> Unit,
    onCreateTournament: () -> Unit
) {
    var showScheduleMenu by remember { mutableStateOf(false) }

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
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
        Box {
            Button(onClick = { showScheduleMenu = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Schedule")
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showScheduleMenu, onDismissRequest = { showScheduleMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Game") },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    onClick = {
                        showScheduleMenu = false
                        onCreateGame()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Tournament") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    onClick = {
                        showScheduleMenu = false
                        onCreateTournament()
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedList(
    uiState: ScheduledGameUiState,
    onGameClick: (gameId: String) -> Unit,
    onTournamentClick: (tournamentId: String) -> Unit
) {
    val items = when (uiState.selectedTab) {
        GamesTab.FEED -> uiState.feedItems
        GamesTab.MY_GAMES -> uiState.myItems
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && items.isEmpty() -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
            items.isEmpty() -> Text(
                text = when (uiState.selectedTab) {
                    GamesTab.FEED -> "No open games or tournaments right now. Schedule one!"
                    GamesTab.MY_GAMES -> "You have no upcoming games or tournaments."
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
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is FeedItem.GameItem -> GameCard(
                            game = item.game,
                            isPlaying = item.game.players.any { it.username == uiState.username },
                            onClick = { onGameClick(item.game.id) }
                        )
                        is FeedItem.TournamentItem -> TournamentCard(
                            tournament = item.tournament,
                            isRegistered = item.tournament.isRegistered(uiState.username),
                            onClick = { onTournamentClick(item.tournament.id) }
                        )
                    }
                }
            }
        }
    }
}
