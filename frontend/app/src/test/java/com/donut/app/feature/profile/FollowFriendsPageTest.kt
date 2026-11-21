package com.donut.app.feature.profile

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.repository.FriendsPageUpdate
import com.donut.app.core.data.repository.FriendsPageUpdates
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendGameDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.donut.app.testing.signedInSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class FollowFriendsPageTest {
    // The stream connects through the fake, so the session's API is never called
    private val session = signedInSession()

    private val playingBob = FriendDto(
        "u2",
        "bob",
        playing = FriendGameDto("game-1", "SINGLES", "2026-11-19T20:00:00.000Z", "Stanley Park")
    )

    private class FakeFriendsPageUpdates(private val stream: Flow<FriendsPageUpdate>) : FriendsPageUpdates {
        override fun updates(session: AuthSession): Flow<FriendsPageUpdate> = stream
    }

    // How many times the stream was connected to (collected)
    private var connections = 0

    private fun viewModel(updates: FriendsPageUpdates) = ProfileViewModel(
        repository = FakeFriendsRepository(),
        equipmentRepository = FakeEquipmentRepository(),
        ioDispatcher = Dispatchers.Unconfined,
        friendsPageUpdates = updates
    )

    // Input: the backend pushes the friends list with bob playing, then one incoming request
    // Expected output: the page shows bob as playing and the request, and is no longer loading either
    @Test
    fun pushedFriendsAndRequestsAreShown() = runBlocking {
        val updates = FakeFriendsPageUpdates(
            flowOf(
                FriendsPageUpdate.Friends(listOf(playingBob)),
                FriendsPageUpdate.Requests(listOf(FriendRequestDto("u3", "carol")))
            )
        )
        val viewModel = viewModel(updates)

        viewModel.followFriendsPage(session)

        val state = viewModel.uiState.value
        assertEquals("Stanley Park", state.friends.single().playing?.courtName)
        assertEquals(listOf("u3"), state.friendRequests.map { it.userId })
        assertEquals(false, state.isLoadingFriends)
        assertEquals(false, state.isLoadingRequests)
    }

    // Input: the first connection drops, the second pushes the friends list, then the backend refuses with 404
    // Expected behavior: it reconnects after the drop, and stops quietly when refused
    // Expected output: two connections, and the friends list from the second
    @Test
    fun reconnectsAfterADroppedConnectionAndStopsWhenRefused() = runBlocking {
        val updates = FakeFriendsPageUpdates(
            flow {
                if (connections++ == 0) throw IOException("connection dropped")
                emit(FriendsPageUpdate.Friends(listOf(playingBob)))
                throw HttpException(Response.error<Unit>(404, "".toResponseBody()))
            }
        )
        val viewModel = viewModel(updates)

        viewModel.followFriendsPage(session)

        assertEquals(2, connections)
        assertEquals(listOf("u2"), viewModel.uiState.value.friends.map { it.userId })
    }
}
