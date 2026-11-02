package com.example.cpen321application.core.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.net.URLEncoder

// Close enough to see the streets around the court
private const val STREET_ZOOM = 16f

// Hides Google's own place pins so the court's pin is the only one on the map
private const val HIDE_PLACES_STYLE = """[{"featureType": "poi", "elementType": "labels", "stylers": [{"visibility": "off"}]}]"""

/**
 * A map centered on a court, with a pin and the court's name on it. The player can
 * pan and zoom, and tapping the pin opens the court in the Google Maps app.
 */
@Composable
fun CourtMap(
    name: String,
    latitude: Double,
    longitude: Double,
    googlePlaceId: String?,
    onTouchedChange: (touched: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val court = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState()
    val markerState = remember(court) { MarkerState(position = court) }
    val properties = remember { MapProperties(mapStyleOptions = MapStyleOptions(HIDE_PLACES_STYLE)) }
    val currentOnTouchedChange by rememberUpdatedState(onTouchedChange)
    val context = LocalContext.current

    // Re-centers the map when the game moves to another court
    LaunchedEffect(court) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(court, STREET_ZOOM)
    }

    GoogleMap(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnTouchedChange(true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        currentOnTouchedChange(false)
                    }
                }
            },
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
    ) {
        MarkerComposable(
            name,
            state = markerState,
            // Opens the court in the Google Maps app
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsUrl(name, latitude, longitude, googlePlaceId)))
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    // No Google Maps app and no browser: the tap does nothing
                }
                true
            }
        ) {
            CourtPin(name)
        }
    }
}

/**
 * The court's name on a tag, above a pin whose tip sits on the court
 */
@Composable
private fun CourtPin(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
    }
}

/**
 * @return a link that opens the court in the Google Maps app, or in the browser without it.
 * With a place ID it opens the place's page, otherwise just the coordinates.
 */
fun googleMapsUrl(name: String, latitude: Double, longitude: Double, placeId: String?): String {
    val url = "https://www.google.com/maps/search/?api=1&query="
    if (placeId == null) {
        return "$url$latitude,$longitude"
    }
    return "$url${URLEncoder.encode(name, "UTF-8")}&query_place_id=$placeId"
}
