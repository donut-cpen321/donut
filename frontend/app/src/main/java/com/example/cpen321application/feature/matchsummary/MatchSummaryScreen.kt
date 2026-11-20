package com.example.cpen321application.feature.matchsummary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.data.model.MatchResult
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.DonutCard
import com.example.cpen321application.core.ui.components.MessageBanner
import com.example.cpen321application.core.ui.components.OutcomeBadge
import com.example.cpen321application.core.ui.components.PlayerAvatar
import com.example.cpen321application.core.ui.components.RatingChangeFooter
import com.example.cpen321application.core.ui.components.RatingDelta
import com.example.cpen321application.core.ui.components.Scoreboard
import com.example.cpen321application.core.ui.components.ScoreboardSide
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import com.example.cpen321application.core.ui.components.StatusChip
import com.example.cpen321application.core.ui.components.UnderlinedTextButton
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.gameTournamentText
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.playerNames
import com.example.cpen321application.core.ui.theme.DonutTextStyles
import kotlin.math.roundToInt

@Composable
fun MatchSummaryRoute(viewModel: MatchSummaryViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    MatchSummaryScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onAddFriend = viewModel::sendFriendRequest,
        onDismissFriendMessage = viewModel::clearFriendMessages
    )
}

@Composable
fun MatchSummaryScreen(
    uiState: MatchSummaryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddFriend: (userId: String) -> Unit,
    onDismissFriendMessage: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(title = "Match summary", onBack = onBack)

        val match = uiState.match
        when {
            match == null && uiState.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            match == null -> LoadError(message = uiState.errorMessage, onRetry = onRetry)
            else -> MatchSummaryContent(
                match = match,
                uiState = uiState,
                onAddFriend = onAddFriend,
                onDismissFriendMessage = onDismissFriendMessage
            )
        }
    }
}

@Composable
private fun LoadError(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message ?: "This match could not be loaded.",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        UnderlinedTextButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun MatchSummaryContent(
    match: MatchResult,
    uiState: MatchSummaryUiState,
    onAddFriend: (userId: String) -> Unit,
    onDismissFriendMessage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ResultCard(match = match)
        ScoreCard(match = match)
        PlayersSection(
            match = match,
            uiState = uiState,
            onAddFriend = onAddFriend,
            onDismissFriendMessage = onDismissFriendMessage
        )
    }
}

@Composable
private fun ResultCard(match: MatchResult) {
    val game = match.game
    DonutCard(sticker = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutcomeBadge(outcome = match.outcome, size = 52.dp, textStyle = DonutTextStyles.numericMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "vs ${playerNames(match.opponents)}", style = MaterialTheme.typography.headlineSmall)
                if (match.partners.isNotEmpty()) {
                    Text(
                        text = "with ${playerNames(match.partners)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = formatStartsAt(game.startsAt),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "${game.court.name} · ${game.format.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        game.tournament?.let {
            StatusChip(
                text = gameTournamentText(it),
                style = ChipStyle.TOURNAMENT,
                icon = R.drawable.ic_emoji_events_filled,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// One row per team, the signed-in user's first, with a column per set; the rating change below
@Composable
private fun ScoreCard(match: MatchResult) {
    val before = match.me.ratingBefore
    val after = match.me.ratingAfter
    Scoreboard(
        title = "Final · ${match.game.format.label}",
        sides = listOf(
            ScoreboardSide(
                name = "You" + teammatesSuffix(match.partners),
                avatarName = match.me.name,
                setScores = match.myScores.map { it.first },
                otherSetScores = match.myScores.map { it.second }
            ),
            ScoreboardSide(
                name = playerNames(match.opponents),
                avatarName = match.opponents.firstOrNull()?.name.orEmpty(),
                setScores = match.myScores.map { it.second },
                otherSetScores = match.myScores.map { it.first }
            )
        ),
        footer = if (before != null && after != null) {
            { RatingChangeFooter(before = before.roundToInt(), after = after.roundToInt()) }
        } else {
            null
        }
    )
}

private fun teammatesSuffix(partners: List<Player>): String =
    if (partners.isEmpty()) "" else " & ${playerNames(partners)}"

@Composable
private fun PlayersSection(
    match: MatchResult,
    uiState: MatchSummaryUiState,
    onAddFriend: (userId: String) -> Unit,
    onDismissFriendMessage: () -> Unit
) {
    val myTeam = match.me.team
    val teams = listOf(myTeam, if (myTeam == 1) 2 else 1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Players", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        FriendMessage(uiState = uiState, onDismiss = onDismissFriendMessage)
        DonutCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            teams.forEachIndexed { index, team ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                match.game.playersOnTeam(team).forEach { player ->
                    PlayerRowWithFriendButton(
                        player = player,
                        isMe = player.id == match.me.id,
                        friendStatusOrNullToHideButton = uiState.friendStatuses[player.id],
                        isSending = player.id in uiState.sendingRequestTo,
                        onAddFriend = { onAddFriend(player.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendMessage(uiState: MatchSummaryUiState, onDismiss: () -> Unit) {
    val isError = uiState.friendErrorMessage != null
    val message = uiState.friendErrorMessage ?: uiState.friendMessage ?: return
    MessageBanner(message = message, isError = isError, onDismiss = onDismiss)
}

@Composable
private fun PlayerRowWithFriendButton(
    player: Player,
    isMe: Boolean,
    friendStatusOrNullToHideButton: FriendStatus?,
    isSending: Boolean,
    onAddFriend: () -> Unit
) {
    val friendStatus = friendStatusOrNullToHideButton
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerAvatar(name = player.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Not the username: until onboarding lets players pick one, it is their email
            Text(
                text = if (isMe) "${player.name} (you)" else player.name,
                style = MaterialTheme.typography.titleMedium
            )
            PlayerRating(player = player)
        }
        if (!isMe && friendStatus != null) {
            FriendButton(status = friendStatus, isSending = isSending, onAddFriend = onAddFriend)
        }
    }
}

@Composable
private fun PlayerRating(player: Player) {
    val before = player.ratingBefore
    val after = player.ratingAfter
    if (before == null || after == null) {
        Text(
            text = "Rating ${player.rating.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${before.roundToInt()} → ${after.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        RatingDelta(change = after.roundToInt() - before.roundToInt())
    }
}

@Composable
private fun FriendButton(status: FriendStatus, isSending: Boolean, onAddFriend: () -> Unit) {
    when (status) {
        FriendStatus.FRIENDS -> StatusChip(text = "Friends", style = ChipStyle.OPEN, icon = R.drawable.ic_check)
        FriendStatus.REQUEST_SENT -> StatusChip(text = "Requested", style = ChipStyle.DONE)
        FriendStatus.REQUEST_RECEIVED -> DonutButton(
            text = "Accept",
            onClick = onAddFriend,
            enabled = !isSending,
            height = SmallButtonHeight,
            icon = R.drawable.ic_check
        )
        FriendStatus.NOT_FRIENDS -> DonutButton(
            text = "Add friend",
            onClick = onAddFriend,
            enabled = !isSending,
            style = ButtonStyle.TONAL,
            height = SmallButtonHeight,
            icon = R.drawable.ic_person_add_filled
        )
    }
}
