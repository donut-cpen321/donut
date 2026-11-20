package com.example.cpen321application.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.core.auth.AuthViewModel
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.ui.FollowWhileVisible
import com.example.cpen321application.core.ui.components.FloatingMessage
import com.example.cpen321application.core.ui.components.SegmentedControl

private val PROFILE_TABS = listOf(ProfileTab.MATCH_HISTORY, ProfileTab.FRIENDS, ProfileTab.EQUIPMENT)

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
    val onOpenStats: () -> Unit = {},
    // Opens a game, e.g. the one a friend is playing
    val onOpenGame: (gameId: String) -> Unit = {},
)

@Composable
fun ProfileRoute(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onOpenMatch: (gameId: String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenGame: (gameId: String) -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val session = authViewModel.session
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(session) {
        viewModel.initSession(session)
    }

    // Live friends and requests only while the Friends tab is on screen: another tab leaves this out, which stops it
    if (session != null && uiState.selectedTab == ProfileTab.FRIENDS) {
        FollowWhileVisible(session) { viewModel.followFriendsPage(session) }
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
        onOpenStats = onOpenStats,
        onOpenGame = onOpenGame,
    )

    ProfileScreen(uiState = uiState, actions = actions)
}

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    actions: ProfileActions = ProfileActions(),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ProfileContent(uiState = uiState, actions = actions)

        // Floats over the top of the page, so a message never moves the page under it
        FloatingMessage(
            message = uiState.errorMessage ?: uiState.successMessage,
            isError = uiState.errorMessage != null,
            onDismiss = actions.onClearMessages,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    if (uiState.showEquipmentDialog) {
        EquipmentDialog(
            uiState = uiState,
            actions = actions,
        )
    }
}

// "Friends · 2" while friend requests are waiting
private fun profileTabTitle(tab: ProfileTab, requests: Int): String = when (tab) {
    ProfileTab.MATCH_HISTORY -> "History"
    ProfileTab.FRIENDS -> if (requests > 0) "Friends · $requests" else "Friends"
    ProfileTab.EQUIPMENT -> "Equipment"
}

// The page itself: the header, the stat tiles, and the selected tab
@Composable
private fun ProfileContent(uiState: ProfileUiState, actions: ProfileActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProfileTopBar(onSignOut = actions.onSignOut)

        ProfileIdentity(name = uiState.name, username = uiState.username)
        StatTiles(
            rating = uiState.elo,
            lastChange = uiState.matchHistory.firstOrNull()?.ratingChange,
            record = recordText(uiState.matchHistory),
            friends = uiState.friendCount,
            onOpenStats = actions.onOpenStats,
        )

        SegmentedControl(
            options = PROFILE_TABS,
            selected = uiState.selectedTab,
            label = { tab -> profileTabTitle(tab, requests = uiState.friendRequests.size) },
            onSelect = actions.onSelectTab,
            role = Role.Tab,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        ProfileTabBody(
            uiState = uiState,
            actions = actions,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
internal fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
