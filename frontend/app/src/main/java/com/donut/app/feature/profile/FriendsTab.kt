package com.donut.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.donut.app.core.ui.components.LiveStatusText
import com.donut.app.core.ui.components.PersonRow
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.components.SmallButtonHeight
import kotlin.math.roundToInt

// Dividers start where the name does: 20 dp gutter + 36 dp avatar + 12 dp
private val DIVIDER_INSET = 68.dp
// The outline of Remove is the error colour at 40%, like every destructive button
private const val REMOVE_OUTLINE_ALPHA = 0.4f

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
            contentPadding = PaddingValues(bottom = 16.dp),
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
            .heightIn(min = 48.dp)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text = text, modifier = Modifier.weight(1f))
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh Friends")
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = DIVIDER_INSET, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun LazyListScope.friendRequestsSection(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.friendRequests.isEmpty()) return

    item(key = "requests-header") {
        ListHeader(text = "Requests · ${uiState.friendRequests.size}", onRefresh = actions.onRefresh)
    }
    itemsIndexed(uiState.friendRequests, key = { _, request -> "request-${request.userId}" }) { index, request ->
        if (index > 0) {
            RowDivider()
        }
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
        itemsIndexed(uiState.friends, key = { _, friend -> "friend-${friend.userId}" }) { index, friend ->
            if (index > 0) {
                RowDivider()
            }
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
    PersonRow(
        name = request.displayName,
        avatarUrl = request.avatarUrl,
        supportingContent = { PersonSubtitle(username = request.username) { MutedLine("Wants to be your friend") } },
    ) {
        Button(
            onClick = onAccept,
            enabled = !isActionInProgress,
            modifier = Modifier.height(SmallButtonHeight),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Accept", modifier = Modifier.padding(start = 6.dp))
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
    val error = MaterialTheme.colorScheme.error
    val status = friendStatusText(friend)
    val isPlaying = friend.playing != null
    PersonRow(
        name = friend.displayName,
        avatarUrl = friend.avatarUrl,
        modifier = if (isPlaying) {
            Modifier.clickable(onClickLabel = "See ${friend.displayName}'s game", onClick = onOpenPlaying)
        } else {
            Modifier
        },
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
        OutlinedButton(
            onClick = onRemove,
            enabled = !isActionInProgress,
            modifier = Modifier
                .height(SmallButtonHeight)
                .semantics { contentDescription = "Remove ${friend.displayName}" },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
            border = BorderStroke(1.dp, error.copy(alpha = REMOVE_OUTLINE_ALPHA)),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text("Remove")
        }
    }
}
