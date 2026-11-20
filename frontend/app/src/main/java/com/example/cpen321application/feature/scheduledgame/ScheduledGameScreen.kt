package com.example.cpen321application.feature.scheduledgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.isRegistered
import com.example.cpen321application.core.ui.FollowWhileVisible
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.RoundIconButton
import com.example.cpen321application.core.ui.components.SegmentedControl
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import com.example.cpen321application.core.ui.components.UnderlinedTextButton
import java.time.LocalDate

@Composable
fun ScheduledGameRoute(
    viewModel: ScheduledGameViewModel,
    onGameClick: (gameId: String) -> Unit,
    onTournamentClick: (tournamentId: String) -> Unit,
    onCreateGame: () -> Unit,
    onCreateTournament: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    FollowWhileVisible(viewModel) { viewModel.followLists() }

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

        SegmentedControl(
            options = GamesTab.entries,
            selected = uiState.selectedTab,
            label = { it.title },
            onSelect = onTabSelected,
            role = Role.Tab,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (uiState.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                UnderlinedTextButton(text = "Retry", onClick = onRefresh)
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
            .heightIn(min = 68.dp)
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Games",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        RoundIconButton(
            icon = R.drawable.ic_refresh,
            contentDescription = "Refresh",
            onClick = onRefresh,
            enabled = !isLoading
        )
        Box {
            DonutButton(
                text = "Schedule",
                icon = R.drawable.ic_add,
                onClick = { showScheduleMenu = true },
                height = SmallButtonHeight
            )
            DropdownMenu(expanded = showScheduleMenu, onDismissRequest = { showScheduleMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Game") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null) },
                    onClick = {
                        showScheduleMenu = false
                        onCreateGame()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Tournament") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_emoji_events), contentDescription = null) },
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
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
            else -> LazyColumn(
                // 14 dp between cards leaves room for the shadows and the tournament trophies
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                feedItems(items, uiState.username, onGameClick, onTournamentClick)
            }
        }
    }
}

// The feed, soonest first; each card's date chip says the day
private fun LazyListScope.feedItems(
    items: List<FeedItem>,
    username: String?,
    onGameClick: (gameId: String) -> Unit,
    onTournamentClick: (tournamentId: String) -> Unit
) {
    val today = LocalDate.now()
    items(items, key = { it.key }) { item ->
        when (item) {
            is FeedItem.GameItem -> GameCard(
                game = item.game,
                isPlaying = item.game.players.any { it.username == username },
                onClick = { onGameClick(item.game.id) },
                today = today
            )
            is FeedItem.TournamentItem -> TournamentCard(
                tournament = item.tournament,
                isRegistered = item.tournament.isRegistered(username),
                onClick = { onTournamentClick(item.tournament.id) },
                today = today
            )
        }
    }
}
