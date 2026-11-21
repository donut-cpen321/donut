package com.donut.app.feature.matchmaking

import com.donut.app.core.data.model.Game

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

// A point on the queue card's map
data class MapPoint(val latitude: Double, val longitude: Double)

/**
 * The other players searching near the player, for the queue card's map
 * @param center where the player is searching from
 * @param players rounded to ~100 m by the backend, without names
 */
data class NearbyPlayers(val center: MapPoint, val players: List<MapPoint>)

data class MatchmakingUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedOption: LocationOption? = null,
    val location: MatchLocation? = null,
    val format: MatchFormat = MatchFormat.EITHER,
    // Waiting in the backend's queue
    val isSearching: Boolean = false,
    // A join or leave request is on its way to the backend
    val isUpdatingQueue: Boolean = false,
    // The players searching nearby while in the queue, null until the first load
    val nearby: NearbyPlayers? = null,
    // The player's matched game waiting for answers; shows the match pop-up
    val match: Game? = null,
    // The match was cancelled while the player was waiting on it
    val matchFellThrough: Boolean = false,
    // An Accept or Cancel is on its way to the backend
    val isAnswering: Boolean = false
) {
    val canFindMatch: Boolean
        get() = location != null && !isLoading && !isSearching && !isUpdatingQueue
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

/**
 * The queue card's title
 * @param location null when the search was picked up after an app restart: the place name is not saved
 */
fun searchingText(location: MatchLocation?): String = when {
    location == null -> "Finding a match"
    location.label == CURRENT_LOCATION_LABEL -> "Finding a match near you"
    else -> "Finding a match near ${location.label}"
}

/**
 * How far apart in rating matched players can be (Product_Design §5.2): 200 in singles, 400 in doubles
 * @return e.g. "±200 rating"
 */
fun ratingRangeText(format: MatchFormat): String = when (format) {
    MatchFormat.SINGLES -> "±200 rating"
    MatchFormat.DOUBLES -> "±400 rating"
    MatchFormat.EITHER -> "±200–400 rating"
}

/**
 * The rules of a match, as the queue card's chips: distance, rating, start time
 */
fun criteriaChips(format: MatchFormat): List<String> =
    listOf("Within 10 km", ratingRangeText(format), "Game in ~1 h")

/**
 * The line under Find a match, e.g. "10 km · ±200 rating · starts ~1 h after match"
 */
fun criteriaText(format: MatchFormat): String = "10 km · ${ratingRangeText(format)} · starts ~1 h after match"

/**
 * @return the time since joining the queue, e.g. "0:42" or "12:05"
 */
fun elapsedText(seconds: Long): String = "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
