package com.example.cpen321application.feature.matchmaking

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.BuildConfig
import com.example.cpen321application.core.ui.theme.Chalk
import com.example.cpen321application.core.ui.theme.CourtGreen
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import kotlinx.coroutines.launch

private const val LOCATION_UNAVAILABLE_MESSAGE = "Could not get your location. Is location turned on?"
private const val PLACE_UNAVAILABLE_MESSAGE = "Could not get that address. Try another one."

private val HOW_IT_WORKS = listOf(
    "Pick where you want to play.",
    "Choose singles, doubles or either.",
    "Play an hour later on a nearby court."
)

@Composable
fun MatchmakingRoute(
    viewModel: MatchmakingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Both options load the same way: show loading, fetch, then report the location or the error
    fun loadLocation(option: LocationOption, failedMessage: String, fetch: suspend () -> MatchLocation?) {
        scope.launch {
            viewModel.onLocationLoading()
            val location = fetch()
            // The user switched to the other option while we were waiting
            if (viewModel.uiState.value.selectedOption != option) {
                return@launch
            }
            if (location == null) {
                viewModel.onLocationFailed(failedMessage)
            } else {
                viewModel.onLocationFound(location)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Either one is enough: the user may only allow an approximate location
        if (results.values.none { it }) {
            viewModel.onPermissionDenied()
            return@rememberLauncherForActivityResult
        }
        loadLocation(LocationOption.CURRENT_LOCATION, LOCATION_UNAVAILABLE_MESSAGE) {
            fetchCurrentLocation(context)
        }
    }

    val addressLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode == PlaceAutocompleteActivity.RESULT_ERROR) {
            val status = PlaceAutocomplete.getResultStatusFromIntent(data)
            viewModel.onLocationFailed("Address search failed: ${status?.statusMessage}")
            return@rememberLauncherForActivityResult
        }
        val prediction = PlaceAutocomplete.getPredictionFromIntent(data)
        if (result.resultCode != PlaceAutocompleteActivity.RESULT_OK || prediction == null) {
            return@rememberLauncherForActivityResult
        }
        val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(data)
        loadLocation(LocationOption.ADDRESS, PLACE_UNAVAILABLE_MESSAGE) {
            fetchPlaceLocation(context, prediction, sessionToken)
        }
    }

    MatchmakingScreen(
        uiState = uiState,
        onUseCurrentLocation = {
            viewModel.onOptionSelected(LocationOption.CURRENT_LOCATION)
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        },
        onEnterAddress = {
            viewModel.onOptionSelected(LocationOption.ADDRESS)
            if (BuildConfig.PLACES_API_KEY.isBlank()) {
                viewModel.onLocationFailed("PLACES_API_KEY is missing. Check local.properties")
                return@MatchmakingScreen
            }
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.PLACES_API_KEY)
            }
            // ponytail: Canada only, since every court we have is in Vancouver
            addressLauncher.launch(PlaceAutocomplete.createIntent(context) { setCountries(listOf("CA")) })
        },
        onFormatSelected = viewModel::onFormatSelected,
        onFindMatch = viewModel::onFindMatch
    )
}

@Composable
fun MatchmakingScreen(
    uiState: MatchmakingUiState,
    onUseCurrentLocation: () -> Unit,
    onEnterAddress: () -> Unit,
    onFormatSelected: (MatchFormat) -> Unit,
    onFindMatch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            CourtHeader()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "How it works", style = MaterialTheme.typography.titleMedium)
                HOW_IT_WORKS.forEachIndexed { index, step -> HowItWorksStep(number = index + 1, text = step) }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "Where do you want to play?", style = MaterialTheme.typography.titleMedium)
                LocationOptionCard(
                    icon = Icons.Filled.LocationOn,
                    text = uiState.textFor(LocationOption.CURRENT_LOCATION),
                    selected = uiState.selectedOption == LocationOption.CURRENT_LOCATION,
                    onClick = onUseCurrentLocation
                )
                LocationOptionCard(
                    icon = Icons.Filled.Edit,
                    text = uiState.textFor(LocationOption.ADDRESS),
                    selected = uiState.selectedOption == LocationOption.ADDRESS,
                    onClick = onEnterAddress
                )
                uiState.errorMessage?.let { ErrorMessage(it) }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "Singles or doubles?", style = MaterialTheme.typography.titleMedium)
                FormatPicker(selected = uiState.format, onSelected = onFormatSelected)
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.isSearching) {
                uiState.location?.let {
                    val near = if (it.label == CURRENT_LOCATION_LABEL) "you" else it.label
                    Text(
                        text = "Finding a match near $near…",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = onFindMatch,
                enabled = uiState.canFindMatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "Find a match", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * The title on a green panel with a faint full court behind it, the one bold element on the screen
 */
@Composable
private fun CourtHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CourtGreen)
            .drawBehind { drawCourtLines() }
            .padding(horizontal = 24.dp, vertical = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Find a match",
                style = MaterialTheme.typography.displaySmall,
                color = Chalk,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Players at your level, courts near you.",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A whole court seen from above, net in the middle, stretched to fill the panel.
 * Lines sit at the real court's proportions along each axis.
 */
private fun DrawScope.drawCourtLines() {
    val paint = Chalk.copy(alpha = 0.18f)
    val stroke = 1.5.dp.toPx()
    val inset = 10.dp.toPx()
    val left = inset
    val right = size.width - inset
    val top = inset
    val bottom = size.height - inset
    val width = right - left
    val height = bottom - top
    // Doubles alleys are 1.37 m of the 10.97 m width; service lines are 6.40 m from the net on a 23.77 m court
    val alley = height * 0.125f
    val serviceOffset = width * 0.269f
    val net = left + width / 2
    val middle = top + height / 2

    drawRect(paint, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(stroke))
    drawLine(paint, Offset(left, top + alley), Offset(right, top + alley), stroke)
    drawLine(paint, Offset(left, bottom - alley), Offset(right, bottom - alley), stroke)
    drawLine(paint, Offset(net - serviceOffset, top + alley), Offset(net - serviceOffset, bottom - alley), stroke)
    drawLine(paint, Offset(net + serviceOffset, top + alley), Offset(net + serviceOffset, bottom - alley), stroke)
    drawLine(paint, Offset(net - serviceOffset, middle), Offset(net + serviceOffset, middle), stroke)
    drawLine(Chalk.copy(alpha = 0.3f), Offset(net, top - inset), Offset(net, bottom + inset), stroke * 2)
}

@Composable
private fun HowItWorksStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun LocationOptionCard(
    icon: ImageVector,
    text: OptionText,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primary else colors.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primary)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                // A picked place can have a long name
                Text(
                    text = text.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = text.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            // Clicks go to the whole card
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun FormatPicker(selected: MatchFormat, onSelected: (MatchFormat) -> Unit) {
    val formats = MatchFormat.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        formats.forEachIndexed { index, format ->
            SegmentedButton(
                selected = format == selected,
                onClick = { onSelected(format) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                modifier = Modifier.height(48.dp)
            ) {
                Text(format.label)
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
