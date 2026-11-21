package com.donut.app.feature.scheduledgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.isRegistered
import com.donut.app.core.ui.FollowWhileVisible
import com.donut.app.core.ui.components.UnderlineTabs
import com.donut.app.core.ui.dayHeaderText
import com.donut.app.core.ui.startDay
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

        UnderlineTabs(
            titles = GamesTab.entries.map { it.title },
            selectedIndex = uiState.selectedTab.ordinal,
            onSelect = { onTabSelected(GamesTab.entries[it]) },
            modifier = Modifier.padding(top = 6.dp)
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
            .padding(start = 20.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Games",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh")
        }
        Box {
            Button(
                onClick = { showScheduleMenu = true },
                modifier = Modifier.height(40.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Schedule", modifier = Modifier.padding(start = 6.dp))
            }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                feedByDay(items, uiState.username, onGameClick, onTournamentClick)
            }
        }
    }
}

// The items under a header per day ("Today · Mon, Nov 16"), soonest first
private fun LazyListScope.feedByDay(
    items: List<FeedItem>,
    username: String?,
    onGameClick: (gameId: String) -> Unit,
    onTournamentClick: (tournamentId: String) -> Unit
) {
    val today = LocalDate.now()
    items.groupBy { startDay(it.startsAt) }.forEach { (day, dayItems) ->
        item(key = "day-$day") {
            Text(
                text = dayHeaderText(day, today).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp, top = 14.dp)
                    .semantics { heading() }
            )
        }
        items(dayItems, key = { it.key }) { item ->
            when (item) {
                is FeedItem.GameItem -> GameCard(
                    game = item.game,
                    isPlaying = item.game.players.any { it.username == username },
                    onClick = { onGameClick(item.game.id) }
                )
                is FeedItem.TournamentItem -> TournamentCard(
                    tournament = item.tournament,
                    isRegistered = item.tournament.isRegistered(username),
                    onClick = { onTournamentClick(item.tournament.id) }
                )
            }
        }
    }
}
