package com.example.cpen321application.feature.matchmaking

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

const val CURRENT_LOCATION_LABEL = "Current location"

private val PLACE_FIELDS = listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)

/**
 * Reads the device's location once
 * @return the location, or null if it could not be read (location turned off, no fix)
 */
// Only called after the user granted the location permission
@SuppressLint("MissingPermission")
suspend fun fetchCurrentLocation(context: Context): MatchLocation? {
    val location = LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .awaitOrNull() ?: return null
    return MatchLocation(CURRENT_LOCATION_LABEL, location.latitude, location.longitude)
}

/**
 * Looks up the coordinates of the place the user picked in the address search
 * @param sessionToken ties this lookup to the search, so Google bills them as one session
 * @return the place's location, or null if the lookup failed or the place has no coordinates
 */
suspend fun fetchPlaceLocation(
    context: Context,
    prediction: AutocompletePrediction,
    sessionToken: AutocompleteSessionToken?
): MatchLocation? {
    val request = FetchPlaceRequest.builder(prediction.placeId, PLACE_FIELDS)
        .setSessionToken(sessionToken)
        .build()
    val place = Places.createClient(context).fetchPlace(request).awaitOrNull()?.place ?: return null
    val latLng = place.location ?: return null
    val label = place.displayName ?: place.formattedAddress ?: prediction.getFullText(null).toString()
    return MatchLocation(label, latLng.latitude, latLng.longitude)
}

private suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resume(null) }
    }
