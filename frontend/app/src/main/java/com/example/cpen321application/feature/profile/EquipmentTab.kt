package com.example.cpen321application.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cpen321application.core.network.dto.EquipmentDto
import com.example.cpen321application.core.network.dto.EquipmentType

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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EquipmentToolbar(
                isActionInProgress = uiState.isActionInProgress,
                onAdd = actions.onOpenAddEquipment,
                onRefresh = actions.onRefresh,
            )
        }

        if (uiState.equipment.isEmpty()) {
            item { EquipmentEmptyView() }
        } else {
            items(uiState.equipment, key = { it.id }) { item ->
                EquipmentItemCard(
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
    isActionInProgress: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onAdd,
            enabled = !isActionInProgress,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Equipment",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Equipment")
        }

        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Equipment")
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
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Equipment Added Yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add your tennis rackets, shoes, and gear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EquipmentItemCard(
    equipment: EquipmentDto,
    isActionInProgress: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EquipmentItemInfo(
                equipment = equipment,
                modifier = Modifier.weight(1f),
            )
            EquipmentItemActions(
                isActionInProgress = isActionInProgress,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun EquipmentItemInfo(
    equipment: EquipmentDto,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = equipment.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (equipment.isDefault) {
                Spacer(modifier = Modifier.width(8.dp))
                DefaultBadge()
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Type: ${equipment.equipmentType.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (equipment.nextMaintenanceAt != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Next Maintenance: ${equipment.nextMaintenanceAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DefaultBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "Default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EquipmentItemActions(
    isActionInProgress: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onEdit,
            enabled = !isActionInProgress,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Equipment",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = onDelete,
            enabled = !isActionInProgress,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Equipment",
                tint = MaterialTheme.colorScheme.error,
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
            Text(
                text = if (isEditing) "Edit Equipment" else "Add Equipment",
                fontWeight = FontWeight.Bold,
            )
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

        Text(
            text = "Equipment Type",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )

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
        label = { Text("Equipment Name") },
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Tennis rackets are automatically scheduled for next maintenance 1 month from today.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(10.dp),
        )
    }
}
