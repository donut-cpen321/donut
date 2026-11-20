package com.example.cpen321application.feature.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cpen321application.R
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.ChipStyle
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.DonutTextField
import com.example.cpen321application.core.ui.components.IconBubble
import com.example.cpen321application.core.ui.components.ListCard
import com.example.cpen321application.core.ui.components.RoundIconButton
import com.example.cpen321application.core.ui.components.SectionLabel
import com.example.cpen321application.core.ui.components.SmallButtonHeight
import com.example.cpen321application.core.ui.components.StatusChip

private const val TENNIS_BALLS_NAME = "Tennis Balls"


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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            items(uiState.equipment, key = { item -> item.id }) { item ->
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
            .heightIn(min = 52.dp)
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(text = if (count == 1) "1 item" else "$count items", modifier = Modifier.weight(1f))
        RoundIconButton(icon = R.drawable.ic_refresh, contentDescription = "Refresh Equipment", onClick = onRefresh)
        DonutButton(
            text = "Add equipment",
            onClick = onAdd,
            enabled = !isActionInProgress,
            height = SmallButtonHeight,
            icon = R.drawable.ic_add,
        )
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
    ListCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBubble(icon = equipment.equipmentType.icon, container = colors.primaryContainer, content = colors.primary)
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
}

@get:DrawableRes
private val EquipmentType.icon: Int
    get() = when (this) {
        EquipmentType.RACKET, EquipmentType.BALLS -> R.drawable.ic_sports_tennis
        EquipmentType.SHOES -> R.drawable.ic_steps
        EquipmentType.OTHER -> R.drawable.ic_backpack
    }

// Name on its own line; then the type (left out when it is the name, e.g. Tennis Balls) and Default
@OptIn(ExperimentalLayoutApi::class)
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
            // The chip moves under the type when the row is too narrow for both
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (type != equipment.name) {
                    Text(
                        text = type,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                if (equipment.isDefault) {
                    StatusChip(text = "Default", style = ChipStyle.OPEN)
                }
            }
        }
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
            DonutButton(
                text = "Save",
                onClick = actions.onSaveEquipment,
                enabled = !uiState.isActionInProgress && (isBalls || uiState.equipmentNameInput.isNotBlank()),
                height = SmallButtonHeight,
            )
        },
        dismissButton = {
            DonutButton(
                text = "Cancel",
                onClick = actions.onCloseEquipmentDialog,
                style = ButtonStyle.SECONDARY,
                height = SmallButtonHeight,
            )
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

    DonutTextField(
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Tennis rackets are automatically scheduled for next maintenance 1 month from today.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
