package com.donut.app.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.donut.app.R
import com.donut.app.core.data.model.Court
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

// Close enough to see the streets around one court
private const val COURT_ZOOM = 15f

// Room around the courts when the map zooms out to show them all
private const val FIT_ALL_PADDING_PX = 120

// The highlighted court's pin draws on top of the others
private const val HIGHLIGHTED_Z_INDEX = 1f

/**
 * A full-screen map of the courts to pick one, with the same courts listed under it.
 * Tapping a pin or a row highlights that court, and Select court picks it.
 * @param courts the courts, empty while they load
 * @param selectedCourtId the court picked so far, null for none
 * @param isLoading whether the courts are still loading
 * @param errorMessage why the courts could not be loaded, null if they could
 * @param onRetry loads the courts again after an error
 * @param onDismiss called when the user closes the picker without picking
 * @param onSelect called with the court the user picked
 */
@Composable
fun CourtPickerDialog(
    courts: List<Court>,
    selectedCourtId: Int?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Court) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                PickerHeader(onDismiss = onDismiss)
                if (isLoading) {
                    CenteredInPicker { CircularProgressIndicator() }
                } else if (errorMessage != null) {
                    CenteredInPicker {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }
                } else if (courts.isEmpty()) {
                    CenteredInPicker { Text("No courts yet.") }
                } else {
                    CourtMapAboveSyncedList(courts = courts, selectedCourtId = selectedCourtId, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
        }
        Text(text = "Pick a court", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CenteredInPicker(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun CourtMapAboveSyncedList(courts: List<Court>, selectedCourtId: Int?, onSelect: (Court) -> Unit) {
    // The map and the list both show this court as highlighted
    var highlightedId by rememberSaveable { mutableStateOf(selectedCourtId) }
    val highlighted = courts.firstOrNull { it.id == highlightedId }
    val cameraPositionState = rememberCameraPositionState()
    // Opens with the picked court's row at the top, like the map opens on its pin
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, courts.indexOf(highlighted)))
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        CourtsMap(
            courts = courts,
            highlightedId = highlightedId,
            cameraPositionState = cameraPositionState,
            // The pin is already on screen, so only the list moves to its row
            onPinTap = { court ->
                highlightedId = court.id
                scope.launch { listState.animateScrollToItem(courts.indexOf(court)) }
            },
            modifier = Modifier.weight(1f)
        )
        HorizontalDivider()
        CourtList(
            courts = courts,
            highlightedId = highlightedId,
            listState = listState,
            // The row is already on screen, so only the map moves to its pin
            onRowTap = { court ->
                highlightedId = court.id
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLngOf(court))) }
            }
        )
        Button(
            onClick = {
                if (highlighted != null) {
                    onSelect(highlighted)
                }
            },
            enabled = highlighted != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(LargeButtonHeight)
        ) {
            Text("Select court")
        }
    }
}

@Composable
private fun CourtsMap(
    courts: List<Court>,
    highlightedId: Int?,
    cameraPositionState: CameraPositionState,
    onPinTap: (Court) -> Unit,
    modifier: Modifier
) {
    val properties = remember { MapProperties(mapStyleOptions = MapStyleOptions(HIDE_PLACES_STYLE)) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Fitting the courts needs the map's size, so the camera only moves once the map is laid out
    LaunchedEffect(isMapLoaded) {
        if (isMapLoaded) {
            cameraPositionState.move(openingCamera(courts, highlightedId))
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxWidth(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        onMapLoaded = { isMapLoaded = true }
    ) {
        courts.forEach { court ->
            key(court.id) {
                val isHighlighted = court.id == highlightedId
                val markerState = remember { MarkerState(position = latLngOf(court)) }
                MarkerComposable(
                    court.id,
                    isHighlighted,
                    state = markerState,
                    zIndex = if (isHighlighted) HIGHLIGHTED_Z_INDEX else 0f,
                    onClick = {
                        onPinTap(court)
                        // Handled here: no info window and no camera jump
                        true
                    }
                ) {
                    if (isHighlighted) {
                        NameTagAbovePin(court.name)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourtList(courts: List<Court>, highlightedId: Int?, listState: LazyListState, onRowTap: (Court) -> Unit) {
    LazyColumn(state = listState, modifier = Modifier.height(220.dp)) {
        items(courts, key = { it.id }) { court ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = court.id == highlightedId,
                        onClick = { onRowTap(court) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = court.id == highlightedId, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(court.name)
            }
        }
    }
}

/**
 * The court the map opens on, instead of zooming out to show every court
 * @return the court picked so far, else the only court, else null to show them all
 */
fun courtToOpenOn(courts: List<Court>, selectedCourtId: Int?): Court? {
    val selected = courts.firstOrNull { it.id == selectedCourtId }
    if (selected != null) {
        return selected
    }
    // A single court can't be fitted: the map would zoom all the way in
    if (courts.size == 1) {
        return courts.first()
    }
    return null
}

private fun openingCamera(courts: List<Court>, selectedCourtId: Int?): CameraUpdate {
    val court = courtToOpenOn(courts, selectedCourtId)
    if (court != null) {
        return CameraUpdateFactory.newLatLngZoom(latLngOf(court), COURT_ZOOM)
    }
    val bounds = LatLngBounds.builder()
    courts.forEach { bounds.include(latLngOf(it)) }
    return CameraUpdateFactory.newLatLngBounds(bounds.build(), FIT_ALL_PADDING_PX)
}

private fun latLngOf(court: Court): LatLng {
    return LatLng(court.latitude, court.longitude)
}
