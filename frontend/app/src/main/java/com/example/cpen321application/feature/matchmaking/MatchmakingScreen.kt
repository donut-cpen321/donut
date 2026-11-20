package com.example.cpen321application.feature.matchmaking

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.cpen321application.BuildConfig
import com.example.cpen321application.core.notifications.rememberAskNotificationPermission
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOCATION_UNAVAILABLE_MESSAGE = "Could not get your location. Is location turned on?"
private const val PLACE_UNAVAILABLE_MESSAGE = "Could not get that address. Try another one."

// How often the queue card's map reloads the players searching nearby
private const val NEARBY_REFRESH_MS = 10_000L

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private typealias LocationLoader = (LocationOption, String, suspend () -> MatchLocation?) -> Unit

/**
 * The Matchmaking tab. The match pop-up and the queue checks live in MatchPopups, so they run on every tab.
 * @param viewModel shared with MatchPopups, so both see the same queue and match
 */
@Composable
fun MatchmakingRoute(viewModel: MatchmakingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val loadLocation = rememberLocationLoader(viewModel)
    val askNotificationPermission = rememberAskNotificationPermission()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // While searching and the Play tab is on screen, the map's dots follow who joins and leaves the queue
    if (uiState.isSearching) {
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    viewModel.refreshNearby()
                    delay(NEARBY_REFRESH_MS)
                }
            }
        }
    }

    MatchmakingScreen(
        uiState = uiState,
        onUseCurrentLocation = rememberOnUseCurrentLocation(viewModel, loadLocation),
        onEnterAddress = rememberOnEnterAddress(viewModel, loadLocation),
        onFormatSelected = viewModel::onFormatSelected,
        onFindMatch = {
            askNotificationPermission()
            scope.launch { viewModel.onFindMatch() }
        },
        onLeaveQueue = { scope.launch { viewModel.onLeaveQueue() } }
    )
}

@Composable
private fun rememberLocationLoader(viewModel: MatchmakingViewModel): LocationLoader {
    val scope = rememberCoroutineScope()
    return { option, failedMessage, fetch ->
        scope.launch { fetchAndReportLocation(viewModel, option, failedMessage, fetch) }
    }
}

// Both location options load the same way: show loading, fetch, then report the location or the error
private suspend fun fetchAndReportLocation(
    viewModel: MatchmakingViewModel,
    option: LocationOption,
    failedMessage: String,
    fetch: suspend () -> MatchLocation?
) {
    viewModel.onLocationLoading()
    val location = fetch()
    // The user switched to the other option while we were waiting
    if (viewModel.uiState.value.selectedOption != option) {
        return
    }
    if (location == null) {
        viewModel.onLocationFailed(failedMessage)
    } else {
        viewModel.onLocationFound(location)
    }
}

@Composable
private fun rememberOnUseCurrentLocation(
    viewModel: MatchmakingViewModel,
    loadLocation: LocationLoader
): () -> Unit {
    val context = LocalContext.current
    // Asks for the location permission, then reads the device's location
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Either one is enough: the user may only allow an approximate location
        if (results.values.any { it }) {
            loadLocation(LocationOption.CURRENT_LOCATION, LOCATION_UNAVAILABLE_MESSAGE) {
                fetchCurrentLocation(context)
            }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    return {
        viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
        permissionLauncher.launch(LOCATION_PERMISSIONS)
    }
}

@Composable
private fun rememberOnEnterAddress(
    viewModel: MatchmakingViewModel,
    loadLocation: LocationLoader
): () -> Unit {
    val context = LocalContext.current
    // Opens the Places address search, then looks up the coordinates of the picked place
    val addressLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleAddressSearchResult(result, context, viewModel, loadLocation)
    }

    return {
        viewModel.onOptionSelected(LocationOption.ADDRESS)
        if (BuildConfig.PLACES_API_KEY.isBlank()) {
            viewModel.onLocationFailed("PLACES_API_KEY is missing. Check local.properties")
        } else {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.PLACES_API_KEY)
            }
            // Only show courts in Canada
            addressLauncher.launch(PlaceAutocomplete.createIntent(context) { setCountries(listOf("CA")) })
        }
    }
}

private fun handleAddressSearchResult(
    result: ActivityResult,
    context: Context,
    viewModel: MatchmakingViewModel,
    loadLocation: LocationLoader
) {
    val data = result.data ?: return
    if (result.resultCode == PlaceAutocompleteActivity.RESULT_ERROR) {
        val status = PlaceAutocomplete.getResultStatusFromIntent(data)
        viewModel.onLocationFailed("Address search failed: ${status?.statusMessage}")
        return
    }
    val prediction = PlaceAutocomplete.getPredictionFromIntent(data)
    if (result.resultCode != PlaceAutocompleteActivity.RESULT_OK || prediction == null) {
        return
    }
    val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(data)
    loadLocation(LocationOption.ADDRESS, PLACE_UNAVAILABLE_MESSAGE) {
        fetchPlaceLocation(context, prediction, sessionToken)
    }
}

/**
 * The Play tab: a court card, how it works, where, singles or doubles, then Find me a match.
 * While searching, the queue card replaces the button.
 */
@Composable
fun MatchmakingScreen(
    uiState: MatchmakingUiState,
    onUseCurrentLocation: () -> Unit,
    onEnterAddress: () -> Unit,
    onFormatSelected: (MatchFormat) -> Unit,
    onFindMatch: () -> Unit,
    onLeaveQueue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            PlayHeader()
            CourtCard()
            HowItWorksSteps()
            LocationSection(
                uiState = uiState,
                onUseCurrentLocation = onUseCurrentLocation,
                onEnterAddress = onEnterAddress
            )
            FormatSection(
                selected = uiState.format,
                // Changing the search means leaving the queue first
                enabled = !uiState.isSearching,
                onSelected = onFormatSelected
            )
        }
        FindMatchBar(uiState = uiState, onFindMatch = onFindMatch, onLeaveQueue = onLeaveQueue)
    }
}
