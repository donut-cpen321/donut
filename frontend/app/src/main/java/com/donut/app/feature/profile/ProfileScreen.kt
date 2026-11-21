package com.donut.app.feature.profile

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.donut.app.core.auth.AuthViewModel
import com.donut.app.core.data.model.Court
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.EquipmentType
import com.donut.app.core.ui.FollowWhileVisible
import com.donut.app.core.ui.components.CourtPickerDialog
import com.donut.app.core.ui.components.FloatingMessage
import com.donut.app.core.ui.components.UnderlineTabs

private val PROFILE_TABS = listOf(
    ProfileTab.MATCH_HISTORY,
    ProfileTab.FRIENDS,
    ProfileTab.EQUIPMENT,
    ProfileTab.PREFERRED_COURTS,
)

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
    val onOpenAddPreferredCourt: () -> Unit = {},
    val onCloseAddPreferredCourtDialog: () -> Unit = {},
    val onAddPreferredCourt: (Court) -> Unit = {},
    val onDeletePreferredCourt: (Int) -> Unit = {},
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
        onRemoveFriend = { userId -> session?.let { viewModel.removeFriend(it, userId) } },
        onAcceptRequest = { userId -> session?.let { viewModel.acceptFriendRequest(it, userId) } },
        onRefresh = { session?.let { viewModel.loadAll(it) } },
        onClearMessages = { viewModel.clearMessages() },
        onOpenAddEquipment = { viewModel.openAddEquipmentDialog() },
        onOpenEditEquipment = { equipment -> viewModel.openEditEquipmentDialog(equipment) },
        onCloseEquipmentDialog = { viewModel.closeEquipmentDialog() },
        onUpdateEquipmentInputs = { name, type, isDefault ->
            viewModel.updateEquipmentInputs(name, type, isDefault)
        },
        onSaveEquipment = { session?.let { viewModel.saveEquipment(it) } },
        onDeleteEquipment = { equipmentId -> session?.let { viewModel.deleteEquipment(it, equipmentId) } },
        onOpenAddPreferredCourt = { session?.let { viewModel.openAddPreferredCourtDialog(it) } },
        onCloseAddPreferredCourtDialog = { viewModel.closeAddPreferredCourtDialog() },
        onAddPreferredCourt = { court -> session?.let { viewModel.addPreferredCourt(it, court) } },
        onDeletePreferredCourt = { courtId -> session?.let { viewModel.deletePreferredCourt(it, courtId) } },
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

    if (uiState.showAddCourtDialog) {
        val availableCourts = uiState.allCourts.filter { court ->
            uiState.preferredCourts.none { it.id == court.id }
        }
        CourtPickerDialog(
            courts = availableCourts,
            selectedCourtId = null,
            isLoading = uiState.isLoadingAllCourts,
            errorMessage = null,
            onRetry = actions.onRefresh,
            onDismiss = actions.onCloseAddPreferredCourtDialog,
            onSelect = actions.onAddPreferredCourt,
        )
    }
}

// The page itself: the header, rating, stats, and the selected tab
@Composable
private fun ProfileContent(uiState: ProfileUiState, actions: ProfileActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProfileTopBar(onSignOut = actions.onSignOut)

        ProfileIdentity(name = uiState.name, username = uiState.username, avatarUrl = uiState.avatarUrl)
        RatingCard(
            rating = uiState.elo,
            lastChange = uiState.matchHistory.firstOrNull()?.ratingChange,
            trend = ratingTrend(uiState.matchHistory),
            onOpenStats = actions.onOpenStats,
        )
        StatsRow(
            stats = listOf(
                recordText(uiState.matchHistory) to "Record",
                uiState.matchHistory.size.toString() to "Matches",
                uiState.friendCount.toString() to "Friends",
            )
        )

        UnderlineTabs(
            titles = listOf("History", "Friends", "Equipment", "Courts"),
            selectedIndex = PROFILE_TABS.indexOf(uiState.selectedTab),
            onSelect = { actions.onSelectTab(PROFILE_TABS[it]) },
            badges = mapOf(PROFILE_TABS.indexOf(ProfileTab.FRIENDS) to uiState.friendRequests.size),
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
            ProfileTab.PREFERRED_COURTS -> PreferredCourtsTabContent(
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
