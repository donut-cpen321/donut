package com.example.cpen321application.feature.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Stands in for the backend: returns whatever each test sets and records what was sent
 */
private class FakeMatchmakingRepository : MatchmakingRepository {
    var joinResult: Result<Unit> = Result.success(Unit)
    var leaveResult: Result<Unit> = Result.success(Unit)
    var queuedResult: Result<MatchFormat?> = Result.success(null)
    val joined = mutableListOf<Pair<MatchFormat, MatchLocation>>()
    var leaveCalls = 0

    override suspend fun joinQueue(format: MatchFormat, location: MatchLocation): Result<Unit> {
        joined.add(format to location)
        return joinResult
    }

    override suspend fun leaveQueue(): Result<Unit> {
        leaveCalls++
        return leaveResult
    }

    override suspend fun queuedFormat(): Result<MatchFormat?> = queuedResult
}

class MatchmakingViewModelTest {
    private val ubc = MatchLocation("UBC Tennis Centre", 49.2606, -123.2460)
    private val repository = FakeMatchmakingRepository()

    private fun newViewModel() = MatchmakingViewModel(repository)

    // A view model already waiting in the queue near UBC
    private fun queuedViewModel(): MatchmakingViewModel {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        runBlocking { viewModel.onFindMatch() }
        return viewModel
    }

    // Input: nothing picked yet
    // Expected output: no option, no location, Find a match disabled
    @Test
    fun startsWithNoLocation() {
        val state = newViewModel().uiState.value

        assertNull(state.selectedOption)
        assertNull(state.location)
        assertFalse(state.canFindMatch)
    }

    // Input: a location was found, then the user picks the other option
    // Expected output: the new option is selected and the old location and error are cleared
    @Test
    fun pickingAnOptionClearsThePreviousLocation() {
        val viewModel = newViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
        viewModel.onLocationFound(ubc)

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        val state = viewModel.uiState.value
        assertEquals(LocationOption.ADDRESS, state.selectedOption)
        assertNull(state.location)
        assertNull(state.errorMessage)
    }

    // Input: the current location is loading, then the user switches to Address
    // Expected output: not loading, since the screen ignores the abandoned lookup when it finishes
    @Test
    fun pickingAnOptionStopsThePreviousLoading() {
        val viewModel = newViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
        viewModel.onLocationLoading()

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        assertFalse(viewModel.uiState.value.isLoading)
    }

    // Input: the user denies the location permission
    // Expected output: an error pointing them to the address option, no location
    @Test
    fun deniedPermissionShowsError() {
        val viewModel = newViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)

        viewModel.onPermissionDenied()

        val state = viewModel.uiState.value
        assertEquals(PERMISSION_DENIED_MESSAGE, state.errorMessage)
        assertNull(state.location)
        assertFalse(state.canFindMatch)
    }

    // Input: the location is being fetched
    // Expected output: loading, Find a match disabled
    @Test
    fun loadingDisablesFindMatch() {
        val viewModel = newViewModel()

        viewModel.onLocationLoading()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.canFindMatch)
    }

    // Input: the location arrives after loading (current location or a picked address)
    // Expected output: location saved, loading done, Find a match enabled
    @Test
    fun foundLocationEnablesFindMatch() {
        val viewModel = newViewModel()
        viewModel.onLocationLoading()

        viewModel.onLocationFound(ubc)

        val state = viewModel.uiState.value
        assertEquals(ubc, state.location)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(state.canFindMatch)
    }

    // Input: the location could not be read (GPS off, Places error)
    // Expected output: the error is shown, loading done, no location
    @Test
    fun failedLocationShowsError() {
        val viewModel = newViewModel()
        viewModel.onLocationLoading()

        viewModel.onLocationFailed("Could not get your location")

        val state = viewModel.uiState.value
        assertEquals("Could not get your location", state.errorMessage)
        assertFalse(state.isLoading)
        assertNull(state.location)
    }

    // Input: Find a match tapped with no location
    // Expected output: nothing happens
    @Test
    fun findMatchWithoutLocationDoesNothing() = runBlocking {
        val viewModel = newViewModel()

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(repository.joined.isEmpty())
    }

    // Input: Doubles picked, location found, Find a match tapped; the backend accepts
    // Expected behavior: joins the queue with that format and location
    // Expected output: searching
    @Test
    fun findMatchJoinsTheQueue() = runBlocking {
        val viewModel = newViewModel()
        viewModel.onFormatSelected(MatchFormat.DOUBLES)
        viewModel.onLocationFound(ubc)

        viewModel.onFindMatch()

        assertEquals(listOf(MatchFormat.DOUBLES to ubc), repository.joined)
        assertTrue(viewModel.uiState.value.isSearching)
        assertFalse(viewModel.uiState.value.isUpdatingQueue)
    }

    // Input: Find a match tapped; the backend is unreachable
    // Expected output: not searching, an error asks to try again
    @Test
    fun findMatchFailureShowsError() = runBlocking {
        repository.joinResult = Result.failure(IOException("offline"))
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)

        viewModel.onFindMatch()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertFalse(state.isUpdatingQueue)
        assertEquals(JOIN_FAILED_MESSAGE, state.errorMessage)
    }

    // Input: Find a match tapped while a search is already running
    // Expected output: Find a match is disabled and a second tap does not join again
    @Test
    fun searchingDisablesFindMatch() = runBlocking {
        val viewModel = queuedViewModel()

        viewModel.onFindMatch()

        assertEquals(1, repository.joined.size)
        val state = viewModel.uiState.value
        assertTrue(state.isSearching)
        assertFalse(state.canFindMatch)
    }

    // Input: a location was found, a new lookup is loading, then Find a match is tapped
    // Expected output: no search starts while the location is loading
    @Test
    fun findMatchWhileLoadingDoesNothing() = runBlocking {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)
        viewModel.onLocationLoading()

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
    }

    // Input: nothing picked yet
    // Expected output: the format is Either, so the player gets the most matches
    @Test
    fun formatStartsAsEither() {
        assertEquals(MatchFormat.EITHER, newViewModel().uiState.value.format)
    }

    // Input: a location was found, then the player picks Doubles
    // Expected output: the format is Doubles and the location is kept
    @Test
    fun pickingAFormatKeepsTheLocation() {
        val viewModel = newViewModel()
        viewModel.onLocationFound(ubc)

        viewModel.onFormatSelected(MatchFormat.DOUBLES)

        val state = viewModel.uiState.value
        assertEquals(MatchFormat.DOUBLES, state.format)
        assertEquals(ubc, state.location)
    }

    // Input: Doubles was picked, then the player switches location option
    // Expected output: the format stays Doubles
    @Test
    fun pickingALocationOptionKeepsTheFormat() {
        val viewModel = newViewModel()
        viewModel.onFormatSelected(MatchFormat.DOUBLES)

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        assertEquals(MatchFormat.DOUBLES, viewModel.uiState.value.format)
    }

    // Input: waiting in the queue, then the player changes the format
    // Expected output: nothing changes, they have to leave the queue first
    @Test
    fun pickingAFormatWhileQueuedDoesNothing() {
        val viewModel = queuedViewModel()

        viewModel.onFormatSelected(MatchFormat.SINGLES)

        val state = viewModel.uiState.value
        assertEquals(MatchFormat.EITHER, state.format)
        assertTrue(state.isSearching)
    }

    // Input: waiting in the queue, then the player picks the other location option
    // Expected output: nothing changes, they have to leave the queue first
    @Test
    fun pickingALocationOptionWhileQueuedDoesNothing() {
        val viewModel = queuedViewModel()

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        val state = viewModel.uiState.value
        assertEquals(ubc, state.location)
        assertTrue(state.isSearching)
    }

    // Input: waiting in the queue, Leave queue tapped; the backend accepts
    // Expected output: not searching, Find a match enabled again
    @Test
    fun leaveQueueStopsSearching() = runBlocking {
        val viewModel = queuedViewModel()

        viewModel.onLeaveQueue()

        val state = viewModel.uiState.value
        assertEquals(1, repository.leaveCalls)
        assertFalse(state.isSearching)
        assertTrue(state.canFindMatch)
    }

    // Input: waiting in the queue, Leave queue tapped; the backend is unreachable
    // Expected output: still searching, an error asks to try again
    @Test
    fun leaveQueueFailureKeepsSearching() = runBlocking {
        repository.leaveResult = Result.failure(IOException("offline"))
        val viewModel = queuedViewModel()

        viewModel.onLeaveQueue()

        val state = viewModel.uiState.value
        assertTrue(state.isSearching)
        assertEquals(LEAVE_FAILED_MESSAGE, state.errorMessage)
    }

    // Input: Leave queue tapped while not in the queue
    // Expected output: the backend is not called
    @Test
    fun leaveQueueWhenNotQueuedDoesNothing() = runBlocking {
        newViewModel().onLeaveQueue()

        assertEquals(0, repository.leaveCalls)
    }

    // Input: the 10-second check while the player is still waiting
    // Expected output: still searching, no match
    @Test
    fun checkQueueWhileWaitingKeepsSearching() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(MatchFormat.EITHER)

        viewModel.checkQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertFalse(viewModel.uiState.value.matchFound)
    }

    // Input: the check after matchmaking took the player out of the queue
    // Expected output: not searching, the match found dialog shows
    @Test
    fun checkQueueAfterMatchShowsMatchFound() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)

        viewModel.checkQueue()

        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(viewModel.uiState.value.matchFound)
    }

    // Input: the check fails because the backend is unreachable
    // Expected output: still searching, a network problem is not a match
    @Test
    fun checkQueueFailureKeepsSearching() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.failure(IOException("offline"))

        viewModel.checkQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertFalse(viewModel.uiState.value.matchFound)
    }

    // Input: the check answers "not in the queue" after the player already left
    // Expected output: no match found dialog, leaving is not a match
    @Test
    fun checkQueueAfterLeavingDoesNothing() = runBlocking {
        val viewModel = queuedViewModel()
        viewModel.onLeaveQueue()
        repository.queuedResult = Result.success(null)

        viewModel.checkQueue()

        assertFalse(viewModel.uiState.value.matchFound)
    }

    // Input: the screen opens while the player is already waiting for doubles (e.g. after an app restart)
    // Expected output: searching again, with the format they are waiting for
    @Test
    fun restoreQueueResumesSearching() = runBlocking {
        repository.queuedResult = Result.success(MatchFormat.DOUBLES)
        val viewModel = newViewModel()

        viewModel.restoreQueue()

        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(MatchFormat.DOUBLES, viewModel.uiState.value.format)
    }

    // Input: the screen opens and the player is not in the queue
    // Expected output: not searching
    @Test
    fun restoreQueueWhenNotQueuedStaysIdle() = runBlocking {
        val viewModel = newViewModel()

        viewModel.restoreQueue()

        assertFalse(viewModel.uiState.value.isSearching)
    }

    // Input: the match found dialog was shown, then closed
    // Expected output: the dialog is gone
    @Test
    fun matchFoundSeenClosesTheDialog() = runBlocking {
        val viewModel = queuedViewModel()
        repository.queuedResult = Result.success(null)
        viewModel.checkQueue()

        viewModel.onMatchFoundSeen()

        assertFalse(viewModel.uiState.value.matchFound)
    }
}
