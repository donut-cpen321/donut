package com.donut.app.feature.leaderboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.donut.app.core.auth.AuthViewModel
import com.donut.app.core.network.dto.LeaderboardEntryDto
import com.donut.app.core.ui.components.PlayerAvatar
import com.donut.app.core.ui.theme.DonutTextStyles
import com.donut.app.core.ui.theme.DonutTheme
import kotlin.math.roundToInt

private const val MUTED_ON_INVERSE = 0.7f
private const val PODIUM = 3
// Wide enough for "#120"; rank, avatar and name are 12 dp apart
private val RANK_WIDTH = 40.dp
// Row dividers start where the avatar ends: 20 dp gutter + rank + 12 dp + 36 dp avatar
private val DIVIDER_INSET = 108.dp

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
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "Leaderboard", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Everyone starts at 1500. Beat stronger players to climb faster.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            uiState.isLoading && uiState.entries.isEmpty() -> CenteredBox {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null && uiState.entries.isEmpty() -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.errorMessage, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
            uiState.entries.isEmpty() -> CenteredBox {
                Text("No players yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        ColumnHeaders()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(uiState.entries, key = { _, entry -> entry.id }) { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = DIVIDER_INSET, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                LeaderboardRow(entry = entry, isMe = index == myIndex)
            }
        }
        if (showPinnedMe && me != null) {
            PinnedMeRow(entry = me)
        }
    }
}

@Composable
private fun ColumnHeaders() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)) {
        Text("RANK", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.width(RANK_WIDTH + 12.dp))
        Text("PLAYER", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
        Text("RATING", style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

// An inverse-surface pill with a ball-yellow edge, floating above the bottom bar
@Composable
private fun PinnedMeRow(entry: LeaderboardEntryDto) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        shape = shape,
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        shadowElevation = 10.dp,
    ) {
        val edge = DonutTheme.colors.highlight
        // The ball-yellow edge is drawn behind the row, so it never changes the row's height
        Box(
            modifier = Modifier.drawBehind {
                val inset = 14.dp.toPx()
                val width = 3.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(0f, inset),
                    size = Size(width, size.height - inset * 2),
                    cornerRadius = CornerRadius(width),
                )
            },
        ) {
            LeaderboardRow(entry = entry, isMe = true, onDarkPill = true)
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntryDto, isMe: Boolean, onDarkPill: Boolean = false) {
    // The pinned "you" row is a dark pill, so its text uses the inverse colours
    val colors = MaterialTheme.colorScheme
    val content = if (onDarkPill) colors.inverseOnSurface else colors.onSurface
    val muted = if (onDarkPill) colors.inverseOnSurface.copy(alpha = MUTED_ON_INVERSE) else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isMe && !onDarkPill) colors.surfaceContainerLow else Color.Transparent)
            .padding(horizontal = if (onDarkPill) 16.dp else 20.dp, vertical = if (onDarkPill) 10.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Rank(rank = entry.rank, color = if (entry.rank <= PODIUM && !onDarkPill) content else muted)
        PlayerAvatar(name = entry.name.ifBlank { entry.username }, avatarUrl = entry.avatarUrl, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) "${entry.name} (You)" else entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${entry.username}",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = entry.rating.roundToInt().toString(),
            style = DonutTextStyles.numericSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = content,
        )
    }
}

// "#1" in mono, with a gold, silver or bronze dot for the top three
@Composable
private fun Rank(rank: Int, color: Color) {
    val donut = DonutTheme.colors
    val medal = when (rank) {
        1 -> donut.medal1
        2 -> donut.medal2
        PODIUM -> donut.medal3
        else -> null
    }
    Row(modifier = Modifier.width(RANK_WIDTH), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "#$rank", style = DonutTextStyles.numericSmall, color = color, maxLines = 1)
        if (medal != null) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(6.dp)
                    .background(medal, CircleShape),
            )
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
