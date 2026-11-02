package com.example.cpen321application.feature.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationOptionTextTest {
    private val ubc = MatchLocation("UBC Tennis Centre", 49.2596, -123.2432)
    private val here = MatchLocation(CURRENT_LOCATION_LABEL, 49.2606, -123.2460)

    // Input: nothing picked yet
    // Expected output: both cards show what they will do
    @Test
    fun nothingPickedShowsTheActions() {
        val state = MatchmakingUiState()

        assertEquals(
            OptionText("Use my current location", "Asks for location permission"),
            state.textFor(LocationOption.CURRENT_LOCATION)
        )
        assertEquals(
            OptionText("Enter an address", "Where you are or where you want to play"),
            state.textFor(LocationOption.ADDRESS)
        )
    }

    // Input: current location picked and still loading
    // Expected output: the current location card says it is getting the location
    @Test
    fun loadingCurrentLocationSaysSo() {
        val state = MatchmakingUiState(selectedOption = LocationOption.CURRENT_LOCATION, isLoading = true)

        assertEquals("Getting your location…", state.textFor(LocationOption.CURRENT_LOCATION).subtitle)
    }

    // Input: current location found
    // Expected output: the card reads "Using my current location", no coordinates
    @Test
    fun foundCurrentLocationSaysUsing() {
        val state = MatchmakingUiState(selectedOption = LocationOption.CURRENT_LOCATION, location = here)

        assertEquals(
            OptionText("Using my current location", "We'll look for courts near you"),
            state.textFor(LocationOption.CURRENT_LOCATION)
        )
    }

    // Input: an address picked
    // Expected output: the address card shows the place name and how to change it
    @Test
    fun pickedAddressShowsThePlace() {
        val state = MatchmakingUiState(selectedOption = LocationOption.ADDRESS, location = ubc)

        assertEquals(
            OptionText("UBC Tennis Centre", "Tap to pick another address"),
            state.textFor(LocationOption.ADDRESS)
        )
    }

    // Input: an address picked
    // Expected output: the current location card is unchanged
    @Test
    fun pickedAddressLeavesTheOtherCardAlone() {
        val state = MatchmakingUiState(selectedOption = LocationOption.ADDRESS, location = ubc)

        assertEquals(
            OptionText("Use my current location", "Asks for location permission"),
            state.textFor(LocationOption.CURRENT_LOCATION)
        )
    }
}
