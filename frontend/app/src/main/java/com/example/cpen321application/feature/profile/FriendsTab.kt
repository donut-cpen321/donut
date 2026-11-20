package com.example.cpen321application.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.ListCard
import com.example.cpen321application.core.ui.components.LiveStatusText
import com.example.cpen321application.core.ui.components.PersonRow
import com.example.cpen321application.core.ui.components.RoundIconButton
import com.example.cpen321application.core.ui.components.SectionLabel
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import kotlin.math.roundToInt

@Composable
internal fun FriendsTabContent(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    // The playing friend whose game is open in a dialog. Read from the live list, so the dialog closes on its own
    // once they stop playing
    var openFriendId by rememberSaveable { mutableStateOf<String?>(null) }

    if (uiState.isLoadingFriends || uiState.isLoadingRequests) {
        LoadingBox()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            friendRequestsSection(uiState = uiState, actions = actions)
            friendsListSection(uiState = uiState, actions = actions, onOpenPlaying = { openFriendId = it })
        }
    }

    val openFriend = uiState.friends.firstOrNull { it.userId == openFriendId }
    val openGame = openFriend?.playing
    if (openFriend != null && openGame != null) {
        FriendPlayingDialog(
            friend = openFriend,
            game = openGame,
            onOpenGame = {
                openFriendId = null
                actions.onOpenGame(openGame.gameId)
            },
            onDismiss = { openFriendId = null },
        )
    }
}

// The label of a section of the list; the first one also holds the refresh button
@Composable
private fun ListHeader(text: String, onRefresh: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text = text, modifier = Modifier.weight(1f))
        if (onRefresh != null) {
            RoundIconButton(icon = R.drawable.ic_refresh, contentDescription = "Refresh Friends", onClick = onRefresh)
        }
    }
}


private fun LazyListScope.friendRequestsSection(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.friendRequests.isEmpty()) return

    item(key = "requests-header") {
        ListHeader(text = "Requests · ${uiState.friendRequests.size}", onRefresh = actions.onRefresh)
    }
    items(uiState.friendRequests, key = { request -> "request-${request.userId}" }) { request ->
        FriendRequestRow(
            request = request,
            isActionInProgress = uiState.isActionInProgress,
            onAccept = { actions.onAcceptRequest(request.userId) },
        )
    }
}

private fun LazyListScope.friendsListSection(
    uiState: ProfileUiState,
    actions: ProfileActions,
    onOpenPlaying: (userId: String) -> Unit,
) {
    item(key = "friends-header") {
        ListHeader(
            text = "Friends · ${uiState.friends.size}",
            // Only the first header gets the refresh button
            onRefresh = if (uiState.friendRequests.isEmpty()) actions.onRefresh else null,
        )
    }

    if (uiState.friends.isEmpty()) {
        item(key = "friends-empty") {
            Text(
                text = "No friends added yet. Add players from a match summary.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }
    } else {
        items(uiState.friends, key = { friend -> "friend-${friend.userId}" }) { friend ->
            FriendRow(
                friend = friend,
                isActionInProgress = uiState.isActionInProgress,
                onRemove = { actions.onRemoveFriend(friend.userId) },
                onOpenPlaying = { onOpenPlaying(friend.userId) },
            )
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequestDto,
    isActionInProgress: Boolean,
    onAccept: () -> Unit,
) {
    ListCard {
        PersonRow(
            name = request.displayName,
            gutter = 12.dp,
            supportingContent = { PersonSubtitle(username = request.username) { MutedLine("Wants to be your friend") } },
        ) {
            DonutButton(
                text = "Accept",
                onClick = onAccept,
                enabled = !isActionInProgress,
                height = SmallButtonHeight,
                icon = R.drawable.ic_check,
            )
        }
    }
}

/**
 * The line under a friend's username: "Playing" while they are in a game (next to the live dot), otherwise their rating
 */
internal fun friendStatusText(friend: FriendDto): String? =
    if (friend.playing != null) "Playing" else friend.rating?.let { "Rating ${it.roundToInt()}" }

// Under a person's name, like the profile header: "@username", then the row's own status line, if any
@Composable
private fun PersonSubtitle(username: String, status: (@Composable () -> Unit)?) {
    Column {
        MutedLine("@$username")
        status?.invoke()
    }
}

// One muted line in the row's second-line style, cut short if it doesn't fit
@Composable
private fun MutedLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// A friend: their name, "@username", then the live dot and "Playing" while they play (tapping the row then
// shows their game) or their rating, with Remove at the end. onOpenPlaying opens the dialog with their game
@Composable
private fun FriendRow(
    friend: FriendDto,
    isActionInProgress: Boolean,
    onRemove: () -> Unit,
    onOpenPlaying: () -> Unit,
) {
    val status = friendStatusText(friend)
    val isPlaying = friend.playing != null
    ListCard {
        PersonRow(
            name = friend.displayName,
            modifier = if (isPlaying) {
                Modifier.clickable(onClickLabel = "See ${friend.displayName}'s game", onClick = onOpenPlaying)
            } else {
                Modifier
            },
            gutter = 12.dp,
            supportingContent = {
                PersonSubtitle(
                    username = friend.username,
                    status = when {
                        status == null -> null
                        isPlaying -> { { LiveStatusText(status) } }
                        else -> { { MutedLine(status) } }
                    },
                )
            },
        ) {
            DonutButton(
                text = "Remove",
                onClick = onRemove,
                enabled = !isActionInProgress,
                style = ButtonStyle.DANGER,
                height = SmallButtonHeight,
                modifier = Modifier.semantics { contentDescription = "Remove ${friend.displayName}" },
            )
        }
    }
}
