package com.donut.app.core.network.dto

import com.google.gson.annotations.SerializedName

enum class EquipmentType(val displayName: String) {
    @SerializedName("RACKET")
    RACKET("Tennis Racket"),

    @SerializedName("BALLS")
    BALLS("Tennis Balls"),

    @SerializedName("SHOES")
    SHOES("Shoes"),

    @SerializedName("OTHER")
    OTHER("Other");

    companion object {
        fun fromString(type: String?): EquipmentType {
            return entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: OTHER
        }
    }
}

data class EquipmentDto(
    @SerializedName("id", alternate = ["_id", "equipmentId"])
    val id: Int,
    val userId: String? = null,
    val type: String,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: String? = null,
    val nextMaintenanceAt: String? = null,
    val retiredAt: String? = null,
    val hoursUsed: Double? = 0.0,
    val gamesUsed: Int? = 0,
) {
    val equipmentType: EquipmentType get() = EquipmentType.fromString(type)
    val effectiveHoursUsed: Double get() = hoursUsed ?: 0.0
    val effectiveGamesUsed: Int get() = gamesUsed ?: 0
}

data class CreateEquipmentRequestDto(
    val type: String,
    val name: String,
    val isDefault: Boolean? = false,
    val nextMaintenanceAt: String? = null,
)

data class UpdateEquipmentRequestDto(
    val type: String? = null,
    val name: String? = null,
    val isDefault: Boolean? = null,
    val nextMaintenanceAt: String? = null,
)
