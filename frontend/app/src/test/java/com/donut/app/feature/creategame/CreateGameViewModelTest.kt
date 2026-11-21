package com.donut.app.feature.creategame

import com.donut.app.core.data.model.Court
import com.donut.app.core.data.model.Format
import com.donut.app.core.data.repository.CourtsRepository
import com.donut.app.testing.UnusedGamesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant

private class FakeCourtsRepository(var result: Result<List<Court>>) : CourtsRepository {
    override suspend fun getCourts(): Result<List<Court>> = result
}

private class FakeGamesRepository : UnusedGamesRepository() {
    var createResult: Result<String> = Result.success("game-1")
    var createCalls = 0

    override suspend fun createGame(format: Format, startsAt: Instant, courtId: Int): Result<String> {
        createCalls++
        return createResult
    }
}

private fun httpError(code: Int, body: String) =
    HttpException(Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())))

// Use Case 4 (create a scheduled match): the failure scenarios of the form
@OptIn(ExperimentalCoroutinesApi::class)
class CreateGameViewModelTest {
    private val court = Court(1, "Stanley Park", 49.3, -123.14)
    private val inOneHour = Instant.now().plus(Duration.ofHours(1))
    private val courts = FakeCourtsRepository(Result.success(listOf(court)))
    private val games = FakeGamesRepository()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    // A form with a start time and a court picked
    private fun filledForm(startsAt: Instant = inOneHour) = CreateGameViewModel(games, courts).apply {
        selectStartsAt(startsAt)
        selectCourt(court)
    }

    // Input: the form is submitted with a start time an hour ago
    // Expected output: the app asks for a start time in the future and sends nothing
    @Test
    fun startTimeInThePastIsNotSent() {
        val viewModel = filledForm(startsAt = Instant.now().minus(Duration.ofHours(1)))
        var created: String? = null

        viewModel.create { created = it }

        assertEquals("Pick a start time in the future.", viewModel.uiState.value.errorMessage)
        assertEquals(0, games.createCalls)
        assertNull(created)
    }

    // Input: the courts can't be loaded, then the user taps Retry once the server is back
    // Expected output: an error with no courts, then the courts and no error
    @Test
    fun courtsThatFailToLoadCanBeRetried() {
        courts.result = Result.failure(IOException("offline"))
        val viewModel = CreateGameViewModel(games, courts)

        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            viewModel.uiState.value.courtsErrorMessage
        )
        assertTrue(viewModel.uiState.value.courts.isEmpty())

        courts.result = Result.success(listOf(court))
        viewModel.loadCourts()

        assertNull(viewModel.uiState.value.courtsErrorMessage)
        assertEquals(listOf(court), viewModel.uiState.value.courts)
    }

    // Input: the backend answers 404 COURT_NOT_FOUND (the court was removed after the list loaded)
    // Expected output: the app shows "Court not found", keeps the form filled in and doesn't open a game
    @Test
    fun courtNotFoundKeepsTheForm() {
        games.createResult = Result.failure(
            httpError(404, """{ "error": "Court not found", "code": "COURT_NOT_FOUND" }""")
        )
        val viewModel = filledForm()
        var created: String? = null

        viewModel.create { created = it }

        val state = viewModel.uiState.value
        assertEquals("Court not found", state.errorMessage)
        assertEquals(court, state.court)
        assertEquals(inOneHour, state.startsAt)
        assertFalse(state.isSubmitting)
        assertNull(created)
    }

    // Input: the server can't be reached when the form is submitted, then the user submits again
    // Expected output: a connection error with the form kept; the second try creates the game
    @Test
    fun serverUnreachableCanBeRetried() {
        games.createResult = Result.failure(IOException("offline"))
        val viewModel = filledForm()
        var created: String? = null

        viewModel.create { created = it }

        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            viewModel.uiState.value.errorMessage
        )
        assertTrue(viewModel.uiState.value.canSubmit)
        assertNull(created)

        games.createResult = Result.success("game-1")
        viewModel.create { created = it }

        assertEquals("game-1", created)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Input: the form is submitted before a court is picked
    // Expected output: nothing is sent, and Schedule game stays disabled
    @Test
    fun noCourtIsNotSent() {
        val viewModel = CreateGameViewModel(games, courts).apply { selectStartsAt(inOneHour) }

        viewModel.create { }

        assertFalse(viewModel.uiState.value.canSubmit)
        assertEquals(0, games.createCalls)
    }
}
