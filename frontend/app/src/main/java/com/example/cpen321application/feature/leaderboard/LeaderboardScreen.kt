package com.example.cpen321application.feature.leaderboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.network.dto.LeaderboardEntryDto
import kotlin.math.roundToInt

@Composable
fun LeaderboardRoute(
    authViewModel: AuthViewModel,
    viewModel: LeaderboardViewModel = viewModel(),
) {
    val session = authViewModel.session
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(session) {
        viewModel.load(session)
    }

    LeaderboardScreen(
        uiState = uiState,
        onRetry = { viewModel.load(session) },
    )
}

@Composable
fun LeaderboardScreen(
    uiState: LeaderboardUiState,
    onRetry: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Leaderboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            uiState.isLoading && uiState.entries.isEmpty() -> CenteredBox {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null && uiState.entries.isEmpty() -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
            uiState.entries.isEmpty() -> CenteredBox {
                Text("No players yet")
            }
            else -> LeaderboardList(uiState)
        }
    }
}

@Composable
private fun LeaderboardList(uiState: LeaderboardUiState) {
    val listState = rememberLazyListState()
    val myIndex = uiState.myIndex
    val me = uiState.me

    // Pin the user's row to the bottom when it is not on screen:
    // either they are outside the top players, or their row is scrolled out of view
    val showPinnedMe by remember(myIndex, me) {
        derivedStateOf {
            when {
                me == null -> false
                myIndex < 0 -> true
                else -> listState.layoutInfo.visibleItemsInfo.none { it.index == myIndex }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(uiState.entries, key = { _, entry -> entry.id }) { index, entry ->
                LeaderboardRow(entry = entry, isMe = index == myIndex)
                HorizontalDivider()
            }
        }
        if (showPinnedMe && me != null) {
            Surface(shadowElevation = 8.dp, tonalElevation = 2.dp) {
                LeaderboardRow(entry = me, isMe = true)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntryDto, isMe: Boolean) {
    val background = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "#${entry.rank}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.width(48.dp),
        )
        Avatar(entry)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) "${entry.name} (You)" else entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${entry.username}",
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = entry.rating.roundToInt().toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

// Initial-letter placeholder: the backend does not send profile pictures yet
@Composable
private fun Avatar(entry: LeaderboardEntryDto) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (entry.name.ifBlank { entry.username }).take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
