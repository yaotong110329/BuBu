package com.kumo.bubu.feature.vehicle

import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.serialization.Serializable
import java.util.UUID

/** Only user inputs and source identity belong in saved state, never loading/saving effects. */
@Serializable
internal data class VehicleFormDraft(
    val draftId: String = UUID.randomUUID().toString(),
    val sourceId: Long?,
    val sourcePublicId: String?,
    val name: String,
    val vehicleType: VehicleType,
    val motorcycleClass: MotorcycleClass?,
    val brand: String,
    val model: String,
    val manufactureYear: String,
    val engineDisplacementCc: String,
    val licensePlate: String,
    val powertrainType: PowertrainType?,
    val trackingStartDate: String,
    val trackingStartOdometerKm: String,
    val note: String,
    val primaryInspectionMonthDay: String,
    val secondaryInspectionMonthDay: String,
) {
    fun toUiState() = VehicleFormUiState(
        vehicleId = sourceId,
        name = name,
        vehicleType = vehicleType,
        motorcycleClass = motorcycleClass,
        brand = brand,
        model = model,
        manufactureYear = manufactureYear,
        engineDisplacementCc = engineDisplacementCc,
        licensePlate = licensePlate,
        powertrainType = powertrainType,
        trackingStartDate = trackingStartDate,
        trackingStartOdometerKm = trackingStartOdometerKm,
        note = note,
        primaryInspectionMonthDay = primaryInspectionMonthDay,
        secondaryInspectionMonthDay = secondaryInspectionMonthDay,
    )
}

internal fun VehicleFormUiState.toDraft(draftId: String, sourcePublicId: String?) = VehicleFormDraft(
    draftId = draftId,
    sourceId = vehicleId,
    sourcePublicId = sourcePublicId,
    name = name,
    vehicleType = vehicleType,
    motorcycleClass = motorcycleClass,
    brand = brand,
    model = model,
    manufactureYear = manufactureYear,
    engineDisplacementCc = engineDisplacementCc,
    licensePlate = licensePlate,
    powertrainType = powertrainType,
    trackingStartDate = trackingStartDate,
    trackingStartOdometerKm = trackingStartOdometerKm,
    note = note,
    primaryInspectionMonthDay = primaryInspectionMonthDay,
    secondaryInspectionMonthDay = secondaryInspectionMonthDay,
)
