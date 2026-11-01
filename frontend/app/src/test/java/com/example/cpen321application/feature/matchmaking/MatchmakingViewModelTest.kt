package com.example.cpen321application.feature.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchmakingViewModelTest {
    private val ubc = MatchLocation("UBC Tennis Centre", 49.2606, -123.2460)

    // Input: nothing picked yet
    // Expected output: no option, no location, Find a match disabled
    @Test
    fun startsWithNoLocation() {
        val state = MatchmakingViewModel().uiState.value

        assertNull(state.selectedOption)
        assertNull(state.location)
        assertFalse(state.canFindMatch)
    }

    // Input: a location was found, then the user picks the other option
    // Expected output: the new option is selected and the old location, error and search are cleared
    @Test
    fun pickingAnOptionClearsThePreviousLocation() {
        val viewModel = MatchmakingViewModel()
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
        viewModel.onLocationFound(ubc)
        viewModel.onFindMatch()

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        val state = viewModel.uiState.value
        assertEquals(LocationOption.ADDRESS, state.selectedOption)
        assertNull(state.location)
        assertNull(state.errorMessage)
        assertFalse(state.isSearching)
    }

    // Input: the user denies the location permission
    // Expected output: an error pointing them to the address option, no location
    @Test
    fun deniedPermissionShowsError() {
        val viewModel = MatchmakingViewModel()
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
        val viewModel = MatchmakingViewModel()

        viewModel.onLocationLoading()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.canFindMatch)
    }

    // Input: the location arrives after loading (current location or a picked address)
    // Expected output: location saved, loading done, Find a match enabled
    @Test
    fun foundLocationEnablesFindMatch() {
        val viewModel = MatchmakingViewModel()
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
        val viewModel = MatchmakingViewModel()
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
    fun findMatchWithoutLocationDoesNothing() {
        val viewModel = MatchmakingViewModel()

        viewModel.onFindMatch()

        assertFalse(viewModel.uiState.value.isSearching)
    }

    // Input: Find a match tapped with a location
    // Expected output: searching
    @Test
    fun findMatchWithLocationStartsSearching() {
        val viewModel = MatchmakingViewModel()
        viewModel.onLocationFound(ubc)

        viewModel.onFindMatch()

        assertTrue(viewModel.uiState.value.isSearching)
    }

    // Input: nothing picked yet
    // Expected output: the format is Either, so the player gets the most matches
    @Test
    fun formatStartsAsEither() {
        assertEquals(MatchFormat.EITHER, MatchmakingViewModel().uiState.value.format)
    }

    // Input: a location was found, then the player picks Doubles
    // Expected output: the format is Doubles and the location is kept
    @Test
    fun pickingAFormatKeepsTheLocation() {
        val viewModel = MatchmakingViewModel()
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
        val viewModel = MatchmakingViewModel()
        viewModel.onFormatSelected(MatchFormat.DOUBLES)

        viewModel.onOptionSelected(LocationOption.ADDRESS)

        assertEquals(MatchFormat.DOUBLES, viewModel.uiState.value.format)
    }

    // Input: searching near a location, then the player changes the format
    // Expected output: the search stops, so the player has to search again with the new format
    @Test
    fun pickingAFormatStopsTheSearch() {
        val viewModel = MatchmakingViewModel()
        viewModel.onLocationFound(ubc)
        viewModel.onFindMatch()

        viewModel.onFormatSelected(MatchFormat.SINGLES)

        assertFalse(viewModel.uiState.value.isSearching)
    }
}
