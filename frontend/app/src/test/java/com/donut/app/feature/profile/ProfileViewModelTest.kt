package com.donut.app.feature.profile

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.auth.User
import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.repository.CourtsRepository
import com.donut.app.core.data.repository.EquipmentRepository
import com.donut.app.core.data.repository.FriendsRepository
import com.donut.app.core.network.ApiService
import com.donut.app.core.network.dto.CreateEquipmentRequestDto
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.EquipmentType
import com.donut.app.core.network.dto.FcmTokenRequestDto
import com.donut.app.core.network.dto.FriendDto
import com.donut.app.core.network.dto.FriendRequestDto
import com.donut.app.core.network.dto.JoinQueueRequestDto
import com.donut.app.core.network.dto.LoginRequestDto
import com.donut.app.core.network.dto.RefreshRequestDto
import com.donut.app.core.network.dto.UpdateEquipmentRequestDto
import com.donut.app.core.network.dto.UserMeDto
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
        FriendDto("f1", "Bob", 1100.0),
        FriendDto("f2", "Charlie", 1050.0)
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
            friendsList.add(FriendDto(req.userId, req.username, 1000.0))
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

    override suspend fun getUserPhoto(session: AuthSession, userId: String): Result<String?> {
        return Result.success("https://example.com/photo.jpg")
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

class FakeCourtsRepository : CourtsRepository {
    val allCourtsList = mutableListOf(
        Court(1, "Kitsilano Courts", 49.27, -123.15),
        Court(2, "Stanley Park Courts", 49.29, -123.14)
    )
    val preferredCourtsList = mutableListOf(
        Court(1, "Kitsilano Courts", 49.27, -123.15)
    )

    override suspend fun getCourts(): Result<List<Court>> {
        return Result.success(allCourtsList)
    }

    override suspend fun getCourts(session: AuthSession): Result<List<Court>> {
        return Result.success(allCourtsList)
    }

    override suspend fun getPreferredCourts(session: AuthSession): Result<List<Court>> {
        return Result.success(preferredCourtsList)
    }

    override suspend fun addPreferredCourt(session: AuthSession, courtId: Int): Result<Unit> {
        val court = allCourtsList.firstOrNull { it.id == courtId }
        if (court != null && preferredCourtsList.none { it.id == courtId }) {
            preferredCourtsList.add(court)
        }
        return Result.success(Unit)
    }

    override suspend fun removePreferredCourt(session: AuthSession, courtId: Int): Result<Unit> {
        preferredCourtsList.removeAll { it.id == courtId }
        return Result.success(Unit)
    }
}

class ProfileViewModelTest {

    private lateinit var repository: FakeFriendsRepository
    private lateinit var equipmentRepository: FakeEquipmentRepository
    private lateinit var courtsRepository: FakeCourtsRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        repository = FakeFriendsRepository()
        equipmentRepository = FakeEquipmentRepository()
        courtsRepository = FakeCourtsRepository()
        viewModel = ProfileViewModel(
            repository = repository,
            equipmentRepository = equipmentRepository,
            courtsRepository = courtsRepository,
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
        viewModel.selectTab(ProfileTab.PREFERRED_COURTS)
        assertEquals(ProfileTab.PREFERRED_COURTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testClearMessages() {
        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun testFetchPreferredCourts() {
        val dummyUser = User("Alice", "access", "refresh")
        val session = createDummySession(dummyUser)

        viewModel.fetchPreferredCourts(session)

        val preferred = viewModel.uiState.value.preferredCourts
        assertEquals(1, preferred.size)
        assertEquals("Kitsilano Courts", preferred.first().name)
    }

    @Test
    fun testAddPreferredCourt() {
        val dummyUser = User("Alice", "access", "refresh")
        val session = createDummySession(dummyUser)

        viewModel.addPreferredCourt(session, Court(2, "Stanley Park Courts", 49.29, -123.14))

        val preferred = viewModel.uiState.value.preferredCourts
        assertEquals(2, preferred.size)
        assertEquals("Preferred court added", viewModel.uiState.value.successMessage)
    }

    @Test
    fun testDeletePreferredCourt() {
        val dummyUser = User("Alice", "access", "refresh")
        val session = createDummySession(dummyUser)

        viewModel.deletePreferredCourt(session, 1)

        val preferred = viewModel.uiState.value.preferredCourts
        assertEquals(0, preferred.size)
        assertEquals("Preferred court removed", viewModel.uiState.value.successMessage)
    }

    @Test
    fun testSaveRacketEquipmentSetsNextMaintenanceAtOneMonth() {
        val dummyUser = User("Alice", "access", "refresh")
        val session = createDummySession(dummyUser)

        viewModel.equipment.openAddEquipmentDialog()
        viewModel.equipment.updateEquipmentInputs("Pro Staff 97", EquipmentType.RACKET, true)
        viewModel.equipment.saveEquipment(session)

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

        viewModel.equipment.openAddEquipmentDialog()
        viewModel.equipment.updateEquipmentInputs("Nike Vapor Shoes", EquipmentType.SHOES, false)
        viewModel.equipment.saveEquipment(session)

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
                override suspend fun getUserPhoto(authorization: String, userId: String) = TODO()
                override suspend fun getEquipment(authorization: String) = TODO()
                override suspend fun createEquipment(authorization: String, request: CreateEquipmentRequestDto) = TODO()
                override suspend fun updateEquipment(authorization: String, id: Int, request: UpdateEquipmentRequestDto) = TODO()
                override suspend fun deleteEquipment(authorization: String, id: Int) = TODO()
                override suspend fun saveFcmToken(authorization: String, request: FcmTokenRequestDto) = TODO()
                override suspend fun getQueueEntry(authorization: String) = TODO()
                override suspend fun joinQueue(authorization: String, request: JoinQueueRequestDto) = TODO()
                override suspend fun leaveQueue(authorization: String) = TODO()
                override suspend fun getLeaderboard(authorization: String, limit: Int?) = TODO()
                override suspend fun getCourts(authorization: String) = TODO()
                override suspend fun getPreferredCourts(authorization: String) = TODO()
                override suspend fun addPreferredCourt(authorization: String, courtId: Int) = TODO()
                override suspend fun removePreferredCourt(authorization: String, courtId: Int) = TODO()
            },
            initialUser = user,
            onUserChanged = {}
        )
    }
}
