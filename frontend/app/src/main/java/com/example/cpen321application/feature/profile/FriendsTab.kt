package com.example.cpen321application.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto

@Composable
internal fun FriendsTabContent(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.isLoadingFriends || uiState.isLoadingRequests) {
        LoadingBox()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = actions.onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Friends")
                    }
                }
            }

            friendRequestsSection(uiState = uiState, actions = actions)
            friendsListSection(uiState = uiState, actions = actions)
        }
    }
}

private fun LazyListScope.friendRequestsSection(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.friendRequests.isEmpty()) return

    item {
        Text(
            text = "Friend Requests (${uiState.friendRequests.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    items(uiState.friendRequests, key = { it.userId }) { request ->
        FriendRequestItem(
            request = request,
            isActionInProgress = uiState.isActionInProgress,
            onAccept = { actions.onAcceptRequest(request.userId) },
        )
    }

    item {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

private fun LazyListScope.friendsListSection(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    item {
        Text(
            text = "Friends (${uiState.friends.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    if (uiState.friends.isEmpty()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No friends added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        items(uiState.friends, key = { it.userId }) { friend ->
            FriendItem(
                friend = friend,
                isActionInProgress = uiState.isActionInProgress,
                onRemove = { actions.onRemoveFriend(friend.userId) },
            )
        }
    }
}

@Composable
private fun ItemAvatar(
    username: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            color = textColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FriendRequestItem(
    request: FriendRequestDto,
    isActionInProgress: Boolean,
    onAccept: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                ItemAvatar(
                    username = request.username,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = request.username,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Wants to be your friend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onAccept,
                enabled = !isActionInProgress,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Accept")
            }
        }
    }
}

@Composable
private fun FriendItem(
    friend: FriendDto,
    isActionInProgress: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                ItemAvatar(
                    username = friend.username,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    textColor = MaterialTheme.colorScheme.onSecondary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = friend.username,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    friend.elo?.let { eloValue ->
                        Text(
                            text = "Elo: $eloValue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onRemove,
                enabled = !isActionInProgress,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Friend",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove")
            }
        }
    }
}
