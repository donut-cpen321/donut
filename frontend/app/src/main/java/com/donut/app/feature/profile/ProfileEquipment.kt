package com.donut.app.feature.profile

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.repository.EquipmentRepository
import com.donut.app.core.network.dto.CreateEquipmentRequestDto
import com.donut.app.core.network.dto.EquipmentDto
import com.donut.app.core.network.dto.EquipmentType
import com.donut.app.core.network.dto.UpdateEquipmentRequestDto
import com.donut.app.core.network.toUserMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Only tennis rackets should have a next maintenance date, set to 1 month from now
private fun nextMaintenanceFor(type: EquipmentType): String? {
    if (type != EquipmentType.RACKET) return null
    val nextMonth = Instant.now().plus(30, ChronoUnit.DAYS)
    return DateTimeFormatter.ISO_INSTANT.format(nextMonth)
}

/**
 * The Equipment tab of the profile: the list, and the dialog to add or edit an item. Shares the profile's state
 * @param uiState the profile's state, which this updates
 * @param scope the profile view model's scope, so requests stop with it
 * @param ioDispatcher where requests run
 * @param repository the /user/equipment routes
 */
class ProfileEquipment(
    private val uiState: MutableStateFlow<ProfileUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val repository: EquipmentRepository,
) {
    fun fetchEquipment(session: AuthSession) {
        scope.launch(ioDispatcher) {
            uiState.update { it.copy(isLoadingEquipment = true) }
            val result = repository.getEquipment(session)
            result.onSuccess { equipmentList ->
                uiState.update {
                    it.copy(
                        equipment = equipmentList,
                        isLoadingEquipment = false,
                    )
                }
            }.onFailure { error ->
                uiState.update {
                    it.copy(
                        isLoadingEquipment = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun openAddEquipmentDialog() {
        uiState.update {
            it.copy(
                showEquipmentDialog = true,
                editingEquipment = null,
                equipmentNameInput = "",
                equipmentTypeInput = EquipmentType.RACKET,
                equipmentIsDefaultInput = false,
            )
        }
    }

    fun openEditEquipmentDialog(equipment: EquipmentDto) {
        val type = equipment.equipmentType
        val name = if (type == EquipmentType.BALLS) "Tennis Balls" else equipment.name
        uiState.update {
            it.copy(
                showEquipmentDialog = true,
                editingEquipment = equipment,
                equipmentNameInput = name,
                equipmentTypeInput = type,
                equipmentIsDefaultInput = equipment.isDefault,
            )
        }
    }

    fun closeEquipmentDialog() {
        uiState.update {
            it.copy(
                showEquipmentDialog = false,
                editingEquipment = null,
                equipmentNameInput = "",
            )
        }
    }

    fun updateEquipmentInputs(name: String, type: EquipmentType, isDefault: Boolean) {
        val effectiveName = if (type == EquipmentType.BALLS) "Tennis Balls" else name
        uiState.update {
            it.copy(
                equipmentNameInput = effectiveName,
                equipmentTypeInput = type,
                equipmentIsDefaultInput = isDefault,
            )
        }
    }

    fun saveEquipment(session: AuthSession) {
        val state = uiState.value
        val type = state.equipmentTypeInput
        val name = if (type == EquipmentType.BALLS) "Tennis Balls" else state.equipmentNameInput.trim()

        if (name.isBlank()) {
            uiState.update { it.copy(errorMessage = "Equipment name cannot be empty") }
            return
        }

        val isDefault = state.equipmentIsDefaultInput
        val editing = state.editingEquipment
        val nextMaintenanceAt = nextMaintenanceFor(type)

        scope.launch(ioDispatcher) {
            uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            if (editing == null) {
                val request = CreateEquipmentRequestDto(
                    type = type.name,
                    name = name,
                    isDefault = isDefault,
                    nextMaintenanceAt = nextMaintenanceAt,
                )
                val result = repository.createEquipment(session, request)
                handleEquipmentSaveResult(session, result, "Equipment added")
            } else {
                val request = UpdateEquipmentRequestDto(
                    type = type.name,
                    name = name,
                    isDefault = isDefault,
                    nextMaintenanceAt = nextMaintenanceAt,
                )
                val result = repository.updateEquipment(session, editing.id, request)
                handleEquipmentSaveResult(session, result, "Equipment updated")
            }
        }
    }

    private fun handleEquipmentSaveResult(
        session: AuthSession,
        result: Result<*>,
        successMessage: String,
    ) {
        result.onSuccess {
            uiState.update {
                it.copy(
                    isActionInProgress = false,
                    showEquipmentDialog = false,
                    successMessage = successMessage,
                )
            }
            fetchEquipment(session)
        }.onFailure { error ->
            uiState.update {
                it.copy(
                    isActionInProgress = false,
                    errorMessage = error.toUserMessage(),
                )
            }
        }
    }

    fun deleteEquipment(session: AuthSession, equipmentId: Int) {
        scope.launch(ioDispatcher) {
            uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
            val result = repository.deleteEquipment(session, equipmentId)
            result.onSuccess {
                uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Equipment removed",
                    )
                }
                fetchEquipment(session)
            }.onFailure { error ->
                uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }
}
