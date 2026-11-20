package com.example.cpen321application.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.MatchResult
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.DonutSetChip
import com.example.cpen321application.core.ui.components.OutcomeBadge
import com.example.cpen321application.core.ui.components.RatingDelta
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.historyPlaceText
import com.example.cpen321application.core.ui.playerNames
import com.example.cpen321application.core.ui.theme.DonutTextStyles

@Composable
internal fun MatchHistoryTabContent(
    matchHistory: List<MatchResult>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenMatch: (gameId: String) -> Unit,
) {
    if (isLoading) {
        LoadingBox()
    } else if (matchHistory.isEmpty()) {
        MatchHistoryEmptyView(onRefresh = onRefresh)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (matchHistory.size == 1) "1 MATCH" else "${matchHistory.size} MATCHES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh Match History")
                    }
                }
            }
            items(matchHistory, key = { match -> match.game.id }) { match ->
                MatchHistoryRow(match = match, onClick = { onOpenMatch(match.game.id) })
            }
        }
    }
}

@Composable
private fun MatchHistoryEmptyView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sports_tennis),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No matches yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Play 1v1 matches to see your past games here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        DonutButton(
            text = "Refresh",
            onClick = onRefresh,
            style = ButtonStyle.SECONDARY,
            height = SmallButtonHeight,
            icon = R.drawable.ic_refresh,
        )
    }
}

// A card per match: the W / L sticker, "vs Name", the set scores as pills, then the Donut set!
// badge or where and when, and the rating change. The whole card opens the match summary.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchHistoryRow(match: MatchResult, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    StickerSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        shadowOffset = 0.dp,
        border = colors.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutcomeBadge(outcome = match.outcome)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "vs ${playerNames(match.opponents)}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    match.myScores.forEach { (mine, theirs) -> SetPill(text = "$mine–$theirs") }
                    if (hasDonutSet(match)) {
                        DonutSetChip()
                    } else {
                        Text(
                            text = historyPlaceText(match),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(start = 2.dp),
                        )
                    }
                }
            }
            match.ratingChange?.let { RatingDelta(change = it) }
        }
    }
}

// A set's score, e.g. "6–4", on a small grey pill
@Composable
private fun SetPill(text: String) {
    Text(
        text = text,
        style = DonutTextStyles.numericSmall.copy(fontSize = 12.sp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
