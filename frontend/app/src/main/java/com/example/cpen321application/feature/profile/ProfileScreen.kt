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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType

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
internal fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
