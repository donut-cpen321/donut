package com.example.cpen321application.feature.profile

import com.example.cpen321application.core.auth.AuthSession
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.repository.EquipmentRepository
import com.example.cpen321application.core.data.repository.FriendsRepository
import com.example.cpen321application.core.network.ApiService
import com.example.cpen321application.core.network.dto.AcceptFriendRequestDto
import com.example.cpen321application.core.network.dto.AcceptMatchRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameLocationRequestDto
import com.example.cpen321application.core.network.dto.ChangeGameTimeRequestDto
import com.example.cpen321application.core.network.dto.CreateEquipmentRequestDto
import com.example.cpen321application.core.network.dto.CreateGameRequestDto
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.network.dto.FcmTokenRequestDto
import com.example.cpen321application.core.network.dto.FriendDto
import com.example.cpen321application.core.network.dto.FriendRequestDto
import com.example.cpen321application.core.network.dto.InviteFriendRequestDto
import com.example.cpen321application.core.network.dto.JoinGameRequestDto
import com.example.cpen321application.core.network.dto.JoinQueueRequestDto
import com.example.cpen321application.core.network.dto.LeaveGameRequestDto
import com.example.cpen321application.core.network.dto.LoginRequestDto
import com.example.cpen321application.core.network.dto.RefreshRequestDto
import com.example.cpen321application.core.network.dto.ReportScoreRequestDto
import com.example.cpen321application.core.network.dto.SendFriendRequestDto
import com.example.cpen321application.core.network.dto.UpdateEquipmentRequestDto
import com.example.cpen321application.core.network.dto.UserMeDto
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeFriendsRepository : FriendsRepository {
    var userMe = UserMeDto(id = "u1", username = "Alice", elo = 1650)
    val friendsList = mutableListOf(
        FriendDto("f1", "Bob", 1100),
        FriendDto("f2", "Charlie", 1050)
    )
    val requestsList = mutableListOf(
        FriendRequestDto("req1", "David")
    )
    val matchesList = mutableListOf<Game>()
    val sentRequests = mutableListOf<String>()

    override suspend fun getUserMe(session: AuthSession): Result<UserMeDto> {
        return Result.success(userMe)
    }

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

    override suspend fun getOutgoingFriendRequests(session: AuthSession): Result<List<FriendRequestDto>> {
        return Result.success(emptyList())
    }

    override suspend fun sendFriendRequest(session: AuthSession, userId: String): Result<Unit> {
        sentRequests.add(userId)
        return Result.success(Unit)
    }

    override suspend fun getMatchHistory(session: AuthSession): Result<List<Game>> {
        return Result.success(matchesList)
    }
}

class FakeEquipmentRepository : EquipmentRepository {
    val equipmentList = mutableListOf(
        EquipmentDto(id = 1, type = "RACKET", name = "Babolat Pure Drive", isDefault = true, nextMaintenanceAt = "2026-10-01T00:00:00.000Z"),
        EquipmentDto(id = 2, type = "BALLS", name = "Wilson US Open Balls", isDefault = false, nextMaintenanceAt = null)
    )
    var lastCreatedRequest: CreateEquipmentRequestDto? = null

    override suspend fun getEquipment(session: AuthSession): Result<List<EquipmentDto>> {
        return Result.success(equipmentList)
    }

    override suspend fun createEquipment(
        session: AuthSession,
        request: CreateEquipmentRequestDto
    ): Result<EquipmentDto> {
        lastCreatedRequest = request
        val newEquipment = EquipmentDto(
            id = equipmentList.size + 1,
            type = request.type,
            name = request.name,
            isDefault = request.isDefault ?: false,
            nextMaintenanceAt = request.nextMaintenanceAt
        )
        equipmentList.add(newEquipment)
        return Result.success(newEquipment)
    }

    override suspend fun updateEquipment(
        session: AuthSession,
        id: Int,
        request: UpdateEquipmentRequestDto
    ): Result<EquipmentDto> {
        val index = equipmentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = equipmentList[index]
            val updated = old.copy(
                type = request.type ?: old.type,
                name = request.name ?: old.name,
                isDefault = request.isDefault ?: old.isDefault,
                nextMaintenanceAt = request.nextMaintenanceAt
            )
            equipmentList[index] = updated
            return Result.success(updated)
        }
        return Result.failure(Exception("Not found"))
    }

    override suspend fun deleteEquipment(session: AuthSession, id: Int): Result<Unit> {
        equipmentList.removeAll { it.id == id }
        return Result.success(Unit)
    }
}

class ProfileViewModelTest {

    private lateinit var repository: FakeFriendsRepository
    private lateinit var equipmentRepository: FakeEquipmentRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        repository = FakeFriendsRepository()
        equipmentRepository = FakeEquipmentRepository()
        viewModel = ProfileViewModel(
            repository = repository,
            equipmentRepository = equipmentRepository,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun testSelectTab() {
        assertEquals(ProfileTab.FRIENDS, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(ProfileTab.MATCH_HISTORY)
        assertEquals(ProfileTab.MATCH_HISTORY, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(ProfileTab.EQUIPMENT)
        assertEquals(ProfileTab.EQUIPMENT, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testClearMessages() {
        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun testSaveRacketEquipmentSetsNextMaintenanceAtOneMonth() {
        val dummyUser = User("Alice", "access", "refresh")
        val session = createDummySession(dummyUser)

        viewModel.openAddEquipmentDialog()
        viewModel.updateEquipmentInputs("Pro Staff 97", EquipmentType.RACKET, true)
        viewModel.saveEquipment(session)

        val created = equipmentRepository.lastCreatedRequest
        assertNotNull(created)
        assertEquals("RACKET", created?.type)
        assertEquals("Pro Staff 97", created?.name)
        assertTrue(created?.isDefault == true)
        assertNotNull(created?.nextMaintenanceAt)
    }

    @Test
    fun testSaveNonRacketEquipmentHasNullNextMaintenanceAt() {
        val dummyUser = User("Alice", "access", "refresh")
        val session = createDummySession(dummyUser)

        viewModel.openAddEquipmentDialog()
        viewModel.updateEquipmentInputs("Nike Vapor Shoes", EquipmentType.SHOES, false)
        viewModel.saveEquipment(session)

        val created = equipmentRepository.lastCreatedRequest
        assertNotNull(created)
        assertEquals("SHOES", created?.type)
        assertNull(created?.nextMaintenanceAt)
    }

    private fun createDummySession(user: User): AuthSession {
        return AuthSession(
            api = object : ApiService {
                override suspend fun loginWithBackend(request: LoginRequestDto) = TODO()
                override suspend fun refreshTokens(request: RefreshRequestDto) = TODO()
                override suspend fun logout(request: RefreshRequestDto) = TODO()
                override suspend fun getUserMe(authorization: String) = TODO()
                override suspend fun getEquipment(authorization: String) = TODO()
                override suspend fun createEquipment(authorization: String, request: CreateEquipmentRequestDto) = TODO()
                override suspend fun updateEquipment(authorization: String, id: Int, request: UpdateEquipmentRequestDto) = TODO()
                override suspend fun deleteEquipment(authorization: String, id: Int) = TODO()
                override suspend fun saveFcmToken(authorization: String, request: FcmTokenRequestDto) = TODO()
                override suspend fun getQueueEntry(authorization: String) = TODO()
                override suspend fun joinQueue(authorization: String, request: JoinQueueRequestDto) = TODO()
                override suspend fun leaveQueue(authorization: String) = TODO()
                override suspend fun getFriends(authorization: String) = TODO()
                override suspend fun removeFriend(authorization: String, userId: String) = TODO()
                override suspend fun getFriendRequests(authorization: String) = TODO()
                override suspend fun acceptFriendRequest(authorization: String, request: AcceptFriendRequestDto) = TODO()
                override suspend fun sendFriendRequest(authorization: String, request: SendFriendRequestDto) = TODO()
                override suspend fun getGameHistory(authorization: String) = TODO()
                override suspend fun getLeaderboard(authorization: String, limit: Int?) = TODO()
                override suspend fun getGameFeed(authorization: String) = TODO()
                override suspend fun getMyGames(authorization: String) = TODO()
                override suspend fun getGame(authorization: String, gameId: String) = TODO()
                override suspend fun createGame(authorization: String, request: CreateGameRequestDto) = TODO()
                override suspend fun cancelGame(authorization: String, gameId: String) = TODO()
                override suspend fun joinGame(authorization: String, request: JoinGameRequestDto) = TODO()
                override suspend fun leaveGame(authorization: String, request: LeaveGameRequestDto) = TODO()
                override suspend fun changeGameTime(authorization: String, request: ChangeGameTimeRequestDto) = TODO()
                override suspend fun changeGameLocation(authorization: String, request: ChangeGameLocationRequestDto) = TODO()
                override suspend fun reportScore(authorization: String, request: ReportScoreRequestDto) = TODO()
                override suspend fun acceptMatch(authorization: String, request: AcceptMatchRequestDto) = TODO()
                override suspend fun inviteToGame(authorization: String, request: InviteFriendRequestDto) = TODO()
                override suspend fun getCourts(authorization: String) = TODO()
            },
            initialUser = user,
            onUserChanged = {}
        )
    }
}
