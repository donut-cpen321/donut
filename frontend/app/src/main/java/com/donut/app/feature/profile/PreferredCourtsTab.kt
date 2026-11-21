package com.donut.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.data.model.Court
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.components.SmallButtonHeight

private val DIVIDER_INSET = 68.dp

@Composable
internal fun PreferredCourtsTabContent(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.isLoadingPreferredCourts) {
        LoadingBox()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "toolbar") {
            PreferredCourtsToolbar(
                count = uiState.preferredCourts.size,
                isActionInProgress = uiState.isActionInProgress,
                onAdd = actions.onOpenAddPreferredCourt,
                onRefresh = actions.onRefresh,
            )
        }

        if (uiState.preferredCourts.isEmpty()) {
            item(key = "empty") { PreferredCourtsEmptyView() }
        } else {
            itemsIndexed(uiState.preferredCourts, key = { _, item -> item.id }) { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = DIVIDER_INSET, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                PreferredCourtRow(
                    court = item,
                    isActionInProgress = uiState.isActionInProgress,
                    onDelete = { actions.onDeletePreferredCourt(item.id) },
                )
            }
        }
    }
}

@Composable
private fun PreferredCourtsToolbar(
    count: Int,
    isActionInProgress: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text = if (count == 1) "1 court" else "$count courts", modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh Preferred Courts")
        }
        Button(
            onClick = onAdd,
            enabled = !isActionInProgress,
            modifier = Modifier.height(SmallButtonHeight),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Add court", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun PreferredCourtsEmptyView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location_on),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(text = "No preferred courts yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Add your favorite courts here to easily access them when scheduling games.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreferredCourtRow(
    court: Court,
    isActionInProgress: Boolean,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.surfaceContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_location_on),
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = court.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${court.latitude}, ${court.longitude}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, enabled = !isActionInProgress) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = "Remove Preferred Court",
                tint = colors.error,
            )
        }
    }
}
