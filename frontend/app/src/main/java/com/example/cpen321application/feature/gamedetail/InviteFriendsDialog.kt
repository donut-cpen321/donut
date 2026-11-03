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
                    status = inviteStatus(invite, friend.userId),
                    isAnotherInviteSending = invite.invitingUserId != null,
                    onInvite = { onInvite(friend.userId) }
                )
            }
        }
    }
}

private enum class InviteStatus { NOT_INVITED, SENDING, NOTIFIED, NOT_NOTIFIED }

private fun inviteStatus(invite: InviteUiState, userId: String): InviteStatus = when {
    invite.invitingUserId == userId -> InviteStatus.SENDING
    invite.invited[userId] == true -> InviteStatus.NOTIFIED
    invite.invited[userId] == false -> InviteStatus.NOT_NOTIFIED
    else -> InviteStatus.NOT_INVITED
}

@Composable
private fun FriendInviteRow(
    friend: FriendDto,
    status: InviteStatus,
    isAnotherInviteSending: Boolean,
    onInvite: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = friend.username, style = MaterialTheme.typography.bodyLarge)
            if (status == InviteStatus.NOT_NOTIFIED) {
                Text(
                    text = "They have notifications off, so let them know yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when (status) {
            InviteStatus.SENDING -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            InviteStatus.NOTIFIED, InviteStatus.NOT_NOTIFIED -> Text(
                text = "Invited",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            InviteStatus.NOT_INVITED -> OutlinedButton(onClick = onInvite, enabled = !isAnotherInviteSending) {
                Text("Invite")
            }
        }
    }
}
