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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.data.model.MatchResult
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.ui.components.LossColor
import com.example.cpen321application.core.ui.components.MatchOutcomeBadge
import com.example.cpen321application.core.ui.components.WinColor
import com.example.cpen321application.core.ui.formatStartsAt
import com.example.cpen321application.core.ui.playerNames
import com.example.cpen321application.core.ui.ratingChangeText
import com.example.cpen321application.core.ui.scoresText

private const val TENNIS_BALLS_NAME = "Tennis Balls"

data class ProfileActions(
    val onSignOut: () -> Unit = {},
    val onSelectTab: (ProfileTab) -> Unit = {},
    val onRemoveFriend: (String) -> Unit = {},
    val onAcceptRequest: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onClearMessages: () -> Unit = {},
    val onOpenAddEquipment: () -> Unit = {},
    val onOpenEditEquipment: (EquipmentDto) -> Unit = {},
    val onCloseEquipmentDialog: () -> Unit = {},
    val onUpdateEquipmentInputs: (String, EquipmentType, Boolean) -> Unit = { _, _, _ -> },
    val onSaveEquipment: () -> Unit = {},
    val onDeleteEquipment: (Int) -> Unit = {},
    val onOpenMatch: (gameId: String) -> Unit = {},
)

@Composable
fun ProfileRoute(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onOpenMatch: (gameId: String) -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val session = authViewModel.session
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(session) {
        viewModel.initSession(session)
    }

    val actions = ProfileActions(
        onSignOut = onSignOut,
        onSelectTab = { viewModel.selectTab(it) },
        onRemoveFriend = { userId -> viewModel.removeFriend(session, userId) },
        onAcceptRequest = { userId -> viewModel.acceptFriendRequest(session, userId) },
        onRefresh = { viewModel.loadAll(session) },
        onClearMessages = { viewModel.clearMessages() },
        onOpenAddEquipment = { viewModel.openAddEquipmentDialog() },
        onOpenEditEquipment = { equipment -> viewModel.openEditEquipmentDialog(equipment) },
        onCloseEquipmentDialog = { viewModel.closeEquipmentDialog() },
        onUpdateEquipmentInputs = { name, type, isDefault ->
            viewModel.updateEquipmentInputs(name, type, isDefault)
        },
        onSaveEquipment = { viewModel.saveEquipment(session) },
        onDeleteEquipment = { equipmentId -> viewModel.deleteEquipment(session, equipmentId) },
        onOpenMatch = onOpenMatch,
    )

    ProfileScreen(uiState = uiState, actions = actions)
}

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    actions: ProfileActions = ProfileActions(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
    ) {
        if ((uiState.errorMessage != null) || (uiState.successMessage != null)) {
            MessageBanner(
                errorMessage = uiState.errorMessage,
                successMessage = uiState.successMessage,
                onDismiss = actions.onClearMessages,
            )
        }

        ProfileHeader(
            username = uiState.username,
            elo = uiState.elo,
            friendCount = uiState.friendCount,
            onSignOut = actions.onSignOut,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ProfileTabs(
            selectedTab = uiState.selectedTab,
            pendingRequestsCount = uiState.friendRequests.size,
            onSelectTab = actions.onSelectTab,
        )

        HorizontalDivider()

        ProfileTabBody(
            uiState = uiState,
            actions = actions,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }

    if (uiState.showEquipmentDialog) {
        EquipmentDialog(
            uiState = uiState,
            actions = actions,
        )
    }
}

@Composable
private fun ProfileTabBody(
    uiState: ProfileUiState,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (uiState.selectedTab) {
            ProfileTab.MATCH_HISTORY -> MatchHistoryTabContent(
                matchHistory = uiState.matchHistory,
                isLoading = uiState.isLoadingMatches,
                onRefresh = actions.onRefresh,
                onOpenMatch = actions.onOpenMatch,
            )
            ProfileTab.FRIENDS -> FriendsTabContent(
                uiState = uiState,
                actions = actions,
            )
            ProfileTab.EQUIPMENT -> EquipmentTabContent(
                uiState = uiState,
                actions = actions,
            )
        }
    }
}

@Composable
private fun MessageBanner(
    errorMessage: String?,
    successMessage: String?,
    onDismiss: () -> Unit,
) {
    val isError = errorMessage != null
    val message = errorMessage ?: successMessage ?: return
    val backgroundColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = textColor,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    username: String,
    elo: Int,
    friendCount: Int,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatar(username = username)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = username.ifEmpty { "User" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatsRow(elo = elo, friendCount = friendCount)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
}

@Composable
private fun UserAvatar(username: String) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatsRow(elo: Int, friendCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatCard(
            label = "1v1 Elo",
            value = "$elo",
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        StatCard(
            label = "Friends",
            value = friendCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProfileTabs(
    selectedTab: ProfileTab,
    pendingRequestsCount: Int,
    onSelectTab: (ProfileTab) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        Tab(
            selected = selectedTab == ProfileTab.MATCH_HISTORY,
            onClick = { onSelectTab(ProfileTab.MATCH_HISTORY) },
            text = { Text("Match History") },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Match History",
                )
            },
        )

        Tab(
            selected = selectedTab == ProfileTab.FRIENDS,
            onClick = { onSelectTab(ProfileTab.FRIENDS) },
            text = { Text("Friends") },
            icon = { FriendsTabIcon(pendingRequestsCount = pendingRequestsCount) },
        )

        Tab(
            selected = selectedTab == ProfileTab.EQUIPMENT,
            onClick = { onSelectTab(ProfileTab.EQUIPMENT) },
            text = { Text("Equipment") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Equipment",
                )
            },
        )
    }
}

@Composable
private fun FriendsTabIcon(pendingRequestsCount: Int) {
    if (pendingRequestsCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text("$pendingRequestsCount") }
            },
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Friends",
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Friends",
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MatchHistoryTabContent(
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Match History")
                    }
                }
            }
            items(matchHistory, key = { it.game.id }) { match ->
                MatchHistoryCard(match = match, onClick = { onOpenMatch(match.game.id) })
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
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Match History Yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Play 1v1 matches to see your past games here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun MatchHistoryCard(match: MatchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "vs ${playerNames(match.opponents)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (match.partners.isNotEmpty()) {
                    Text(
                        text = "with ${playerNames(match.partners)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatStartsAt(match.game.startsAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = scoresText(match.myScores),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                MatchOutcomeBadge(outcome = match.outcome)
                match.ratingChange?.let { change ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ratingChangeText(change),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            change > 0 -> WinColor
                            change < 0 -> LossColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendsTabContent(
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

@Composable
private fun EquipmentTabContent(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.isLoadingEquipment) {
        LoadingBox()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EquipmentToolbar(
                isActionInProgress = uiState.isActionInProgress,
                onAdd = actions.onOpenAddEquipment,
                onRefresh = actions.onRefresh,
            )
        }

        if (uiState.equipment.isEmpty()) {
            item { EquipmentEmptyView() }
        } else {
            items(uiState.equipment, key = { it.id }) { item ->
                EquipmentItemCard(
                    equipment = item,
                    isActionInProgress = uiState.isActionInProgress,
                    onEdit = { actions.onOpenEditEquipment(item) },
                    onDelete = { actions.onDeleteEquipment(item.id) },
                )
            }
        }
    }
}

@Composable
private fun EquipmentToolbar(
    isActionInProgress: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onAdd,
            enabled = !isActionInProgress,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Equipment",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Equipment")
        }

        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Equipment")
        }
    }
}

@Composable
private fun EquipmentEmptyView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Equipment Added Yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add your tennis rackets, shoes, and gear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EquipmentItemCard(
    equipment: EquipmentDto,
    isActionInProgress: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EquipmentItemInfo(
                equipment = equipment,
                modifier = Modifier.weight(1f),
            )
            EquipmentItemActions(
                isActionInProgress = isActionInProgress,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun EquipmentItemInfo(
    equipment: EquipmentDto,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = equipment.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (equipment.isDefault) {
                Spacer(modifier = Modifier.width(8.dp))
                DefaultBadge()
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Type: ${equipment.equipmentType.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (equipment.nextMaintenanceAt != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Next Maintenance: ${equipment.nextMaintenanceAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DefaultBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "Default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EquipmentItemActions(
    isActionInProgress: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onEdit,
            enabled = !isActionInProgress,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Equipment",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = onDelete,
            enabled = !isActionInProgress,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Equipment",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EquipmentDialog(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    val isEditing = uiState.editingEquipment != null
    val isBalls = uiState.equipmentTypeInput == EquipmentType.BALLS

    AlertDialog(
        onDismissRequest = actions.onCloseEquipmentDialog,
        title = {
            Text(
                text = if (isEditing) "Edit Equipment" else "Add Equipment",
                fontWeight = FontWeight.Bold,
            )
        },
        text = { EquipmentDialogBody(uiState = uiState, actions = actions) },
        confirmButton = {
            Button(
                onClick = actions.onSaveEquipment,
                enabled = !uiState.isActionInProgress && (isBalls || uiState.equipmentNameInput.isNotBlank()),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = actions.onCloseEquipmentDialog) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EquipmentDialogBody(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EquipmentNameField(uiState = uiState, actions = actions)

        Text(
            text = "Equipment Type",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )

        EquipmentTypeSelector(uiState = uiState, actions = actions)

        DefaultEquipmentCheckbox(uiState = uiState, actions = actions)

        if (uiState.equipmentTypeInput == EquipmentType.RACKET) {
            RacketMaintenanceNote()
        }
    }
}

@Composable
private fun EquipmentNameField(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    val isBalls = uiState.equipmentTypeInput == EquipmentType.BALLS

    OutlinedTextField(
        value = if (isBalls) TENNIS_BALLS_NAME else uiState.equipmentNameInput,
        onValueChange = { name ->
            if (!isBalls) {
                actions.onUpdateEquipmentInputs(
                    name,
                    uiState.equipmentTypeInput,
                    uiState.equipmentIsDefaultInput,
                )
            }
        },
        enabled = !isBalls,
        label = { Text("Equipment Name") },
        supportingText = if (isBalls) {
            { Text("Name is fixed for Tennis Balls") }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EquipmentTypeSelector(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    val entries = EquipmentType.entries

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Row 1: Tennis Racket, Tennis Balls
        EquipmentTypeChipRow(
            types = entries.take(2),
            uiState = uiState,
            actions = actions,
        )
        // Row 2: Shoes, Other
        EquipmentTypeChipRow(
            types = entries.drop(2),
            uiState = uiState,
            actions = actions,
        )
    }
}

@Composable
private fun EquipmentTypeChipRow(
    types: List<EquipmentType>,
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { type ->
            FilterChip(
                selected = uiState.equipmentTypeInput == type,
                onClick = {
                    if (uiState.equipmentTypeInput != type) {
                        // Reset the name whenever the selected type changes
                        val newName = if (type == EquipmentType.BALLS) TENNIS_BALLS_NAME else ""
                        actions.onUpdateEquipmentInputs(
                            newName,
                            type,
                            uiState.equipmentIsDefaultInput,
                        )
                    }
                },
                label = { Text(type.displayName) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DefaultEquipmentCheckbox(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = uiState.equipmentIsDefaultInput,
            onCheckedChange = { isChecked ->
                actions.onUpdateEquipmentInputs(
                    uiState.equipmentNameInput,
                    uiState.equipmentTypeInput,
                    isChecked,
                )
            },
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Set as default equipment")
    }
}

@Composable
private fun RacketMaintenanceNote() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Tennis rackets are automatically scheduled for next maintenance 1 month from today.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(10.dp),
        )
    }
}