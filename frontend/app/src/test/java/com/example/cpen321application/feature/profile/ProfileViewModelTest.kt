package com.example.cpen321application.feature.profile

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.data.repository.FriendsRepository
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.network.dto.MatchDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakeFriendsRepository : FriendsRepository {
    val friendsList = mutableListOf(
        FriendDto("f1", "Bob", 1100),
        FriendDto("f2", "Charlie", 1050)
    )
    val requestsList = mutableListOf(
        FriendRequestDto("req1", "David")
    )
    val matchesList = mutableListOf(
        MatchDto("m1", "Bob", "WIN", "3-1", "2025-01-01", 15)
    )

    override suspend fun getFriends(session: AuthSession): Result<List<FriendDto>> {
        return Result.success(friendsList)
    }

    override suspend fun removeFriend(session: AuthSession, userId: String): Result<Unit> {
        friendsList.removeAll { it.userId == userId }
        return Result.success(Unit)
    }

    override suspend fun getFriendRequests(session: AuthSession): Result<List<FriendRequestDto>> {
        return Result.success(requestsList)
    }

    override suspend fun acceptFriendRequest(session: AuthSession, userId: String): Result<Unit> {
        val req = requestsList.find { it.userId == userId }
        if (req != null) {
            requestsList.remove(req)
            friendsList.add(FriendDto(req.userId, req.username, 1000))
        }
        return Result.success(Unit)
    }

    override suspend fun getMatchHistory(session: AuthSession): Result<List<MatchDto>> {
        return Result.success(matchesList)
    }
}

class ProfileViewModelTest {

    private lateinit var repository: FakeFriendsRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        repository = FakeFriendsRepository()
        viewModel = ProfileViewModel(repository)
    }

    @Test
    fun testSelectTab() {
        assertEquals(ProfileTab.FRIENDS, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(ProfileTab.MATCH_HISTORY)
        assertEquals(ProfileTab.MATCH_HISTORY, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testClearMessages() {
        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
    }
}
