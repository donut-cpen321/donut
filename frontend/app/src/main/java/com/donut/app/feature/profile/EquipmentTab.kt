package com.donut.app.feature.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donut.app.R
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.EquipmentType
import com.donut.app.core.ui.components.ChipStyle
import com.donut.app.core.ui.components.SectionLabel
import com.donut.app.core.ui.components.SmallButtonHeight
import com.donut.app.core.ui.components.StatusChip
import com.donut.app.core.ui.theme.TextFieldShape
import java.util.Locale

private const val TENNIS_BALLS_NAME = "Tennis Balls"

// Dividers start where the name does: 20 dp gutter + 36 dp tile + 12 dp
private val DIVIDER_INSET = 68.dp

@Composable
internal fun EquipmentTabContent(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    if (uiState.isLoadingEquipment) {
        LoadingBox()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "toolbar") {
            EquipmentToolbar(
                count = uiState.equipment.size,
                isActionInProgress = uiState.isActionInProgress,
                onAdd = actions.onOpenAddEquipment,
                onRefresh = actions.onRefresh,
            )
        }

        if (uiState.equipment.isEmpty()) {
            item(key = "empty") { EquipmentEmptyView() }
        } else {
            itemsIndexed(uiState.equipment, key = { _, item -> item.id }) { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = DIVIDER_INSET, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                EquipmentRow(
                    equipment = item,
                    isActionInProgress = uiState.isActionInProgress,
                    onEdit = { actions.onOpenEditEquipment(item) },
                    onDelete = { actions.onDeleteEquipment(item.id) },
                )
            }
        }
    }
}

@Composable
private fun EquipmentToolbar(
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
        SectionLabel(text = if (count == 1) "1 item" else "$count items", modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh Equipment")
        }
        Button(
            onClick = onAdd,
            enabled = !isActionInProgress,
            modifier = Modifier.height(SmallButtonHeight),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Add equipment", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun EquipmentEmptyView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sports_tennis),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(text = "No equipment yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Add your rackets, balls, shoes and other gear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// An icon tile for the type, the name (with Default), the type and next service, then edit and delete
@Composable
private fun EquipmentRow(
    equipment: EquipmentDto,
    isActionInProgress: Boolean,
    onEdit: () -> Unit,
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
                painter = painterResource(equipment.equipmentType.icon),
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        EquipmentItemInfo(equipment = equipment, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit, enabled = !isActionInProgress) {
            Icon(
                painterResource(R.drawable.ic_edit),
                contentDescription = "Edit Equipment",
                tint = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, enabled = !isActionInProgress) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete Equipment", tint = colors.error)
        }
    }
}

@get:DrawableRes
private val EquipmentType.icon: Int
    get() = when (this) {
        EquipmentType.RACKET, EquipmentType.BALLS -> R.drawable.ic_sports_tennis
        EquipmentType.SHOES -> R.drawable.ic_steps
        EquipmentType.OTHER -> R.drawable.ic_backpack
    }

// Name on its own line; then the type (left out when it is the name, e.g. Tennis Balls) and Default
@Composable
private fun EquipmentItemInfo(
    equipment: EquipmentDto,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val type = equipment.equipmentType.displayName
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = equipment.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (type != equipment.name || equipment.isDefault) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type != equipment.name) {
                    Text(text = type, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                if (equipment.isDefault) {
                    StatusChip(text = "Default", style = ChipStyle.OPEN)
                }
            }
        }
        Text(
            text = equipmentUsageText(equipment.effectiveGamesUsed, equipment.effectiveHoursUsed),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        if (equipment.nextMaintenanceAt != null) {
            Text(
                text = "Service due ${equipment.nextMaintenanceAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
internal fun EquipmentDialog(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    val isEditing = uiState.editingEquipment != null
    val isBalls = uiState.equipmentTypeInput == EquipmentType.BALLS

    AlertDialog(
        onDismissRequest = actions.onCloseEquipmentDialog,
        title = {
            Text(text = if (isEditing) "Edit equipment" else "Add equipment")
        },
        text = { EquipmentDialogBody(uiState = uiState, actions = actions) },
        confirmButton = {
            Button(
                onClick = actions.onSaveEquipment,
                enabled = !uiState.isActionInProgress && (isBalls || uiState.equipmentNameInput.isNotBlank()),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = actions.onCloseEquipmentDialog) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EquipmentDialogBody(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EquipmentNameField(uiState = uiState, actions = actions)

        SectionLabel(text = "Type")

        EquipmentTypeSelector(uiState = uiState, actions = actions)

        DefaultEquipmentCheckbox(uiState = uiState, actions = actions)

        if (uiState.equipmentTypeInput == EquipmentType.RACKET) {
            RacketMaintenanceNote()
        }
    }
}

@Composable
private fun EquipmentNameField(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    val isBalls = uiState.equipmentTypeInput == EquipmentType.BALLS

    OutlinedTextField(
        value = if (isBalls) TENNIS_BALLS_NAME else uiState.equipmentNameInput,
        onValueChange = { name ->
            if (!isBalls) {
                actions.onUpdateEquipmentInputs(
                    name,
                    uiState.equipmentTypeInput,
                    uiState.equipmentIsDefaultInput,
                )
            }
        },
        enabled = !isBalls,
        label = { Text("Name") },
        supportingText = if (isBalls) {
            { Text("Name is fixed for Tennis Balls") }
        } else null,
        singleLine = true,
        shape = TextFieldShape,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EquipmentTypeSelector(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    val entries = EquipmentType.entries

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Row 1: Tennis Racket, Tennis Balls
        EquipmentTypeChipRow(
            types = entries.take(2),
            uiState = uiState,
            actions = actions,
        )
        // Row 2: Shoes, Other
        EquipmentTypeChipRow(
            types = entries.drop(2),
            uiState = uiState,
            actions = actions,
        )
    }
}

@Composable
private fun EquipmentTypeChipRow(
    types: List<EquipmentType>,
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { type ->
            FilterChip(
                selected = uiState.equipmentTypeInput == type,
                onClick = {
                    if (uiState.equipmentTypeInput != type) {
                        // Reset the name whenever the selected type changes
                        val newName = if (type == EquipmentType.BALLS) TENNIS_BALLS_NAME else ""
                        actions.onUpdateEquipmentInputs(
                            newName,
                            type,
                            uiState.equipmentIsDefaultInput,
                        )
                    }
                },
                label = { Text(type.displayName) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DefaultEquipmentCheckbox(
    uiState: ProfileUiState,
    actions: ProfileActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = uiState.equipmentIsDefaultInput,
            onCheckedChange = { isChecked ->
                actions.onUpdateEquipmentInputs(
                    uiState.equipmentNameInput,
                    uiState.equipmentTypeInput,
                    isChecked,
                )
            },
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Set as default equipment")
    }
}

@Composable
private fun RacketMaintenanceNote() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Tennis rackets are automatically scheduled for next maintenance 1 month from today.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * How much an item was used, e.g. "3 games used · 3 hrs used" or "1 game used · 1.5 hrs used"
 * @param games the games played with it
 * @param hours the hours played with it, shown with one decimal unless whole
 */
internal fun equipmentUsageText(games: Int, hours: Double): String {
    val gamesText = if (games == 1) "1 game" else "$games games"
    val hoursNumber = if (hours % 1.0 == 0.0) hours.toInt().toString() else String.format(Locale.US, "%.1f", hours)
    val hoursText = if (hours == 1.0) "1 hr" else "$hoursNumber hrs"
    return "$gamesText used · $hoursText used"
}
