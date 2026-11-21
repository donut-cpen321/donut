package com.donut.app.feature.gamedetail

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.GameEquipment
import com.donut.app.core.data.model.GameSource
import com.donut.app.core.data.model.GameStatus
import com.donut.app.core.data.model.Player
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.testing.UnusedEquipmentRepository
import com.donut.app.testing.UnusedGamesRepository
import com.donut.app.testing.signedInSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

// Stands in for PUT /games/equipment: returns what each test sets and records what was saved
private class FakeGames : UnusedGamesRepository() {
    var result: Result<Unit> = Result.success(Unit)
    val saved = mutableListOf<List<Int>>()

    override suspend fun setGameEquipment(gameId: String, equipmentIds: List<Int>): Result<Unit> {
        saved.add(equipmentIds)
        return result
    }
}

// Stands in for GET /user/equipment, counting the loads
private class FakeEquipment(var result: Result<List<EquipmentDto>>) : UnusedEquipmentRepository() {
    var loads = 0

    override suspend fun getEquipment(session: AuthSession): Result<List<EquipmentDto>> {
        loads++
        return result
    }
}

private fun httpError(code: Int, body: String) =
    HttpException(Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())))

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailEquipmentTest {
    private val racket = EquipmentDto(id = 7, type = "RACKET", name = "Pro Staff")
    private val balls = EquipmentDto(id = 9, type = "BALLS", name = "Tennis Balls")
    private val games = FakeGames()
    private val items = FakeEquipment(Result.success(listOf(racket, balls)))

    // A singles game between alice (the signed-in user) and bob; alice uses her racket, bob his own
    private fun game(status: GameStatus = GameStatus.BOOKED) = Game(
        id = "game-1",
        format = Format.SINGLES,
        status = status,
        source = GameSource.SCHEDULED,
        startsAt = Instant.parse("2026-11-22T18:00:00Z"),
        court = Court(1, "Kitsilano Beach Park", 49.27, -123.15),
        players = listOf(Player("u1", "alice", "Alice A", 1500.0, 1), Player("u2", "bob", "Bob B", 1500.0, 2)),
        rounds = emptyList(),
        equipment = listOf(GameEquipment(7, "u1", "RACKET", "Pro Staff"), GameEquipment(3, "u2", "RACKET", "Blade"))
    )

    // The game screen's state showing the game with alice's racket picked
    private val state = MutableStateFlow(
        GameDetailUiState(game = game(), selectedEquipmentIds = myEquipmentIds(game(), "alice"))
    )

    private fun equipment() =
        GameDetailEquipment("game-1", state, TestScope(UnconfinedTestDispatcher()), games, items, signedInSession())

    // Input: the game with alice's racket and bob's racket
    // Expected output: only alice's racket for alice, nothing for someone who isn't playing
    @Test
    fun myEquipmentIdsAreTheSignedInPlayersOnly() {
        assertEquals(setOf(7), myEquipmentIds(game(), "alice"))
        assertEquals(setOf(3), myEquipmentIds(game(), "bob"))
        assertEquals(emptySet<Int>(), myEquipmentIds(game(), "carol"))
        assertEquals(emptySet<Int>(), myEquipmentIds(game(), null))
    }

    // Input: the screen opens
    // Expected output: alice's items, to pick from
    @Test
    fun loadShowsTheUsersItems() {
        equipment().load()

        assertEquals(listOf(racket, balls), state.value.userEquipment)
        assertEquals(false, state.value.isLoadingEquipment)
    }

    // Input: alice's items can't be loaded
    // Expected output: no items, and an error
    @Test
    fun loadFailureShowsAnError() {
        items.result = Result.failure(IOException("offline"))

        equipment().load()

        assertTrue(state.value.userEquipment.isEmpty())
        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            state.value.equipmentErrorMessage
        )
    }

    // Input: alice taps the balls, then the racket
    // Expected output: each tap saves the whole selection: racket and balls, then the balls alone
    @Test
    fun toggleAddsAndRemovesItems() {
        val equipment = equipment()

        equipment.toggle(9)
        assertEquals(setOf(7, 9), state.value.selectedEquipmentIds)
        equipment.toggle(7)
        assertEquals(setOf(9), state.value.selectedEquipmentIds)

        assertEquals(listOf(listOf(7, 9), listOf(9)), games.saved.map { it.sorted() })
        assertNull(state.value.equipmentErrorMessage)
    }

    // Input: the backend refuses the change because the game ended meanwhile (409 GAME_CLOSED)
    // Expected output: the selection goes back, and the backend's reason is shown
    @Test
    fun toggleFailureRestoresTheSelection() {
        games.result = Result.failure(
            httpError(409, """{ "error": "Game is already completed or cancelled", "code": "GAME_CLOSED" }""")
        )

        equipment().toggle(9)

        assertEquals(setOf(7), state.value.selectedEquipmentIds)
        assertEquals("Game is already completed or cancelled", state.value.equipmentErrorMessage)
        assertEquals(0, items.loads)
    }

    // Input: the item was deleted on another screen (404 EQUIPMENT_NOT_FOUND)
    // Expected output: the selection goes back, and alice's items are loaded again
    @Test
    fun toggleOfADeletedItemReloadsTheItems() {
        games.result = Result.failure(
            httpError(404, """{ "error": "Equipment not found", "code": "EQUIPMENT_NOT_FOUND" }""")
        )

        equipment().toggle(9)

        assertEquals(setOf(7), state.value.selectedEquipmentIds)
        assertEquals(1, items.loads)
    }

    // Input: alice taps an item on a completed game
    // Expected output: nothing changes and nothing is sent
    @Test
    fun toggleOnAClosedGameDoesNothing() {
        state.value = state.value.copy(game = game(GameStatus.COMPLETED))

        equipment().toggle(9)

        assertEquals(setOf(7), state.value.selectedEquipmentIds)
        assertTrue(games.saved.isEmpty())
    }
}
