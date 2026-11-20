package com.example.cpen321application.feature.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.network.dto.LeaderboardEntryDto
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.PlayerAvatar
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.StickerBorderWidth
import com.example.cpen321application.core.ui.theme.StickerShadowOffset
import kotlin.math.roundToInt

// The top three stand on a podium once there are at least three players
private const val PODIUM = 3
// Muted text on the clay "You" row is the on-primary colour at 80%
private const val MUTED_ON_PRIMARY = 0.8f
// Wide enough for "#120"
private val RANK_WIDTH = 40.dp
// The podium's middle column (first place) is a little wider and its block taller
private const val FIRST_PLACE_WEIGHT = 1.1f

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
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Leaderboard",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
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
                    DonutButton(text = "Retry", onClick = onRetry, height = SmallButtonHeight)
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
    val hasPodium = uiState.entries.size >= PODIUM
    val rows = if (hasPodium) uiState.entries.drop(PODIUM) else uiState.entries
    // Where the user's own row sits in the list: the podium is item 0, then a row per player
    val myItem = when {
        myIndex < 0 -> -1
        hasPodium && myIndex < PODIUM -> 0
        hasPodium -> myIndex - PODIUM + 1
        else -> myIndex
    }

    // Pin the user's row to the bottom when it is not on screen:
    // either they are outside the top players, or their row is scrolled out of view
    val showPinnedMe by remember(myItem, me) {
        derivedStateOf {
            when {
                me == null -> false
                myItem < 0 -> true
                else -> listState.layoutInfo.visibleItemsInfo.none { it.index == myItem }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (hasPodium) {
                item(key = "podium") {
                    Podium(top = uiState.entries.take(PODIUM), meId = me?.id)
                }
            }
            items(rows, key = { entry -> entry.id }) { entry ->
                LeaderboardRow(entry = entry, isMe = entry.id == me?.id && myIndex >= 0)
            }
        }
        if (showPinnedMe && me != null) {
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)) {
                LeaderboardRow(entry = me, isMe = true)
            }
        }
    }
}

private fun displayName(entry: LeaderboardEntryDto, isMe: Boolean): String =
    if (isMe) "${entry.name} (You)" else entry.name

// Second, first and third on gold, silver and bronze blocks, then an ink line above the rest
@Composable
private fun Podium(top: List<LeaderboardEntryDto>, meId: String?) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(1, 0, 2).forEach { index ->
                val entry = top[index]
                PodiumColumn(
                    entry = entry,
                    isMe = entry.id == meId,
                    modifier = Modifier.weight(if (index == 0) FIRST_PLACE_WEIGHT else 1f),
                )
            }
        }
        HorizontalDivider(thickness = StickerBorderWidth, color = DonutTheme.colors.stickerBorder)
        Spacer(modifier = Modifier.size(6.dp))
    }
}

@Composable
private fun PodiumColumn(entry: LeaderboardEntryDto, isMe: Boolean, modifier: Modifier = Modifier) {
    val donut = DonutTheme.colors
    val (medal, avatarSize, blockHeight) = when (entry.rank) {
        1 -> Triple(donut.medal1, 64.dp, 78.dp)
        2 -> Triple(donut.medal2, 52.dp, 56.dp)
        else -> Triple(donut.medal3, 52.dp, 42.dp)
    }
    // Dark in both themes, so it reads on gold, silver and bronze
    val onMedal = MaterialTheme.colorScheme.onTertiary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            PlayerAvatar(name = entry.name.ifBlank { entry.username }, size = avatarSize)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 4.dp)
                    .size(24.dp)
                    .background(medal, CircleShape)
                    .border(StickerBorderWidth, donut.stickerBorder, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(text = entry.rank.toString(), style = DonutTextStyles.numericSmall.copy(fontWeight = FontWeight.ExtraBold), color = onMedal)
            }
        }
        Text(
            text = displayName(entry, isMe),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "@${entry.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.rating.roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StickerSurface(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(blockHeight),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = medal,
            contentColor = onMedal,
            shadowOffset = 0.dp,
        ) {
            Text(text = "#${entry.rank}", style = DonutTextStyles.numericMedium, modifier = Modifier.align(Alignment.Center))
        }
    }
}

// A white card per player; the signed-in user's is a clay sticker
@Composable
private fun LeaderboardRow(entry: LeaderboardEntryDto, isMe: Boolean) {
    val colors = MaterialTheme.colorScheme
    val content = if (isMe) colors.onPrimary else colors.onSurface
    val muted = if (isMe) colors.onPrimary.copy(alpha = MUTED_ON_PRIMARY) else colors.onSurfaceVariant
    StickerSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isMe) colors.primary else colors.surfaceContainerLowest,
        contentColor = content,
        shadowOffset = if (isMe) StickerShadowOffset else 0.dp,
        border = if (isMe) DonutTheme.colors.stickerBorder else colors.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "#${entry.rank}",
                style = DonutTextStyles.numericMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                color = muted,
                maxLines = 1,
                modifier = Modifier.width(RANK_WIDTH),
            )
            PlayerAvatar(name = entry.name.ifBlank { entry.username }, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName(entry, isMe),
                    style = MaterialTheme.typography.titleMedium,
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
                style = DonutTextStyles.numericSmall,
                modifier = Modifier
                    .background(if (isMe) content.copy(alpha = RATING_PILL_ALPHA) else colors.surfaceContainerHigh, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

// The rating pill on the clay row is the on-primary colour at 22%
private const val RATING_PILL_ALPHA = 0.22f

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
