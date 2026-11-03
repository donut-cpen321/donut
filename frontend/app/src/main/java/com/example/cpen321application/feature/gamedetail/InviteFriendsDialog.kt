package com.example.cpen321application.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.network.dto.FriendDto

/**
 * Lists the user's friends who are not in the game, each with a button that sends them an invite
 * @param friends the friends who can be invited, null until they load
 * @param invite the friends list's loading state and the invites sent so far
 * @param onRetry loads the friends again after an error
 * @param onInvite invites the friend with this user id
 * @param onDismiss closes the dialog
 */
@Composable
fun InviteFriendsDialog(
    friends: List<FriendDto>?,
    invite: InviteUiState,
    onRetry: () -> Unit,
    onInvite: (userId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite friends") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InviteFriendsContent(friends = friends, invite = invite, onRetry = onRetry, onInvite = onInvite)
                if (invite.errorMessage != null) {
                    Text(
                        text = invite.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun InviteFriendsContent(
    friends: List<FriendDto>?,
    invite: InviteUiState,
    onRetry: () -> Unit,
    onInvite: (userId: String) -> Unit
) {
    when {
        invite.friendsErrorMessage != null -> Column {
            Text(text = invite.friendsErrorMessage, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        friends == null -> CircularProgressIndicator()
        friends.isEmpty() -> Text(
            text = "No friends to invite. Friends already in this game aren't listed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friends, key = { it.userId }) { friend ->
                FriendInviteRow(
                    friend = friend,
                    notified = invite.invited[friend.userId],
                    isInviting = invite.invitingUserId == friend.userId,
                    canInvite = invite.invitingUserId == null,
                    onInvite = { onInvite(friend.userId) }
                )
            }
        }
    }
}

/**
 * @param notified null until the friend is invited, then whether their device got the notification
 * @param isInviting this friend's invite has no answer yet
 * @param canInvite no other invite is waiting for an answer
 */
@Composable
private fun FriendInviteRow(
    friend: FriendDto,
    notified: Boolean?,
    isInviting: Boolean,
    canInvite: Boolean,
    onInvite: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = friend.username, style = MaterialTheme.typography.bodyLarge)
            if (notified == false) {
                Text(
                    text = "They have notifications off, so let them know yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            isInviting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            notified != null -> Text(
                text = "Invited",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            else -> OutlinedButton(onClick = onInvite, enabled = canInvite) { Text("Invite") }
        }
    }
}
