package com.example.cpen321application.feature.matchsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.data.model.MatchResult
import com.example.cpen321application.core.data.model.Player
import com.example.cpen321application.core.data.model.playersOnTeam
import com.example.cpen321application.core.ui.components.BackHeader
import com.example.cpen321application.core.ui.components.LossColor
import com.example.cpen321application.core.ui.components.MatchOutcomeBadge
import com.example.cpen321application.core.ui.components.WinColor
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.gameTournamentText
import com.example.cpen321application.core.ui.label
import com.example.cpen321application.core.ui.playerNames
import com.example.cpen321application.core.ui.ratingChangeText
import kotlin.math.roundToInt

private val ScoreColumnWidth = 40.dp

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
        TextButton(onClick = onRetry) { Text("Retry") }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MatchOutcomeBadge(outcome = match.outcome, textStyle = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "vs ${playerNames(match.opponents)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (match.partners.isNotEmpty()) {
                Text(text = "with ${playerNames(match.partners)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = formatStartsAt(game.startsAt), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${game.court.name} · ${game.format.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            game.tournament?.let {
                Text(
                    text = gameTournamentText(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            match.ratingChange?.let { change -> MyRatingChange(me = match.me, change = change) }
        }
    }
}

@Composable
private fun MyRatingChange(me: Player, change: Int) {
    val before = me.ratingBefore ?: return
    val after = me.ratingAfter ?: return
    Spacer(modifier = Modifier.size(8.dp))
    Text(text = "Your rating", style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${before.roundToInt()} → ${after.roundToInt()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        RatingChange(change = change)
    }
}

@Composable
private fun RatingChange(change: Int) {
    Text(
        text = ratingChangeText(change),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = when {
            change > 0 -> WinColor
            change < 0 -> LossColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

// One row per team, the signed-in user's first, with a column per set
@Composable
private fun ScoreCard(match: MatchResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Score", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                match.myScores.indices.forEach { index ->
                    Text(
                        text = "Set ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(ScoreColumnWidth)
                    )
                }
            }
            ScoreRow(name = "You" + teammatesSuffix(match.partners), scores = match.myScores)
            ScoreRow(name = playerNames(match.opponents), scores = match.myScores.map { it.second to it.first })
        }
    }
}

private fun teammatesSuffix(partners: List<Player>): String =
    if (partners.isEmpty()) "" else " & ${playerNames(partners)}"

/**
 * @param scores this team's score and the other team's score in each set; the sets this team won are bold
 */
@Composable
private fun ScoreRow(name: String, scores: List<Pair<Int, Int>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        scores.forEach { (own, other) ->
            Text(
                text = own.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (own > other) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(ScoreColumnWidth)
            )
        }
    }
}

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
        Text(text = "Players", style = MaterialTheme.typography.titleMedium)
        FriendMessage(uiState = uiState, onDismiss = onDismissFriendMessage)
        teams.forEachIndexed { index, team ->
            if (index > 0) HorizontalDivider()
            match.game.playersOnTeam(team).forEach { player ->
                PlayerRow(
                    player = player,
                    isMe = player.id == match.me.id,
                    friendStatus = uiState.friendStatuses[player.id],
                    isSending = player.id in uiState.sendingRequestTo,
                    onAddFriend = { onAddFriend(player.id) }
                )
            }
        }
    }
}

@Composable
private fun FriendMessage(uiState: MatchSummaryUiState, onDismiss: () -> Unit) {
    val isError = uiState.friendErrorMessage != null
    val message = uiState.friendErrorMessage ?: uiState.friendMessage ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

/**
 * @param friendStatus the signed-in user's friendship with this player, null for the user themselves
 *   or before the friends list loads
 */
@Composable
private fun PlayerRow(
    player: Player,
    isMe: Boolean,
    friendStatus: FriendStatus?,
    isSending: Boolean,
    onAddFriend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerAvatar(name = player.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Not the username: until onboarding lets players pick one, it is their email
            Text(
                text = if (isMe) "${player.name} (you)" else player.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
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
        RatingChange(change = after.roundToInt() - before.roundToInt())
    }
}

@Composable
private fun PlayerAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FriendButton(status: FriendStatus, isSending: Boolean, onAddFriend: () -> Unit) {
    when (status) {
        FriendStatus.FRIENDS -> OutlinedButton(onClick = {}, enabled = false) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Friends")
        }
        FriendStatus.REQUEST_SENT -> OutlinedButton(onClick = {}, enabled = false) {
            Text("Requested")
        }
        FriendStatus.REQUEST_RECEIVED -> Button(onClick = onAddFriend, enabled = !isSending) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Accept")
        }
        FriendStatus.NOT_FRIENDS -> Button(onClick = onAddFriend, enabled = !isSending) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add friend")
        }
    }
}
