package com.example.cpen321application.feature.matchmaking

enum class LocationOption { CURRENT_LOCATION, ADDRESS }

// Same values as the backend's FormatPreference
enum class MatchFormat(val label: String) {
    SINGLES("Singles"),
    DOUBLES("Doubles"),
    EITHER("Either")
}

/**
 * Where the user wants to be matched
 * @param label what the screen shows, an address or "Current location"
 */
data class MatchLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

data class MatchmakingUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedOption: LocationOption? = null,
    val location: MatchLocation? = null,
    val format: MatchFormat = MatchFormat.EITHER,
    val isSearching: Boolean = false
) {
    val canFindMatch: Boolean
        get() = location != null && !isLoading
}

data class OptionText(val title: String, val subtitle: String)

/**
 * What a location card says: what it will do, that it is loading, or what it picked
 */
fun MatchmakingUiState.textFor(option: LocationOption): OptionText {
    val isPicked = selectedOption == option
    if (option == LocationOption.CURRENT_LOCATION) {
        if (isPicked && isLoading) {
            return OptionText("Use my current location", "Getting your location…")
        }
        if (isPicked && location != null) {
            return OptionText("Using my current location", "We'll look for courts near you")
        }
        return OptionText("Use my current location", "Asks for location permission")
    }
    if (isPicked && isLoading) {
        return OptionText("Enter an address", "Looking up the address…")
    }
    if (isPicked && location != null) {
        return OptionText(location.label, "Tap to pick another address")
    }
    return OptionText("Enter an address", "Where you are or where you want to play")
}
