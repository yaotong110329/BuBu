package com.kumo.bubu.feature.fuel

import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import kotlinx.serialization.Serializable

@Serializable
internal data class FuelFormDraft(
    val draftId: String,
    val sourceId: Long?,
    val sourcePublicId: String?,
    val selectedVehiclePublicId: String?,
    val selectedVehicleId: Long?,
    val date: String,
    val time: String,
    val odometerKm: String,
    val volumeLiters: String,
    val pricePerLiter: String,
    val totalCostTwd: String,
    val isFullTank: Boolean,
    val fuelProduct: FuelProduct?,
    val fuelingMode: FuelingMode,
    val fuelEconomyStatisticsStatus: FuelEconomyStatisticsStatus,
    val note: String,
    val priceSource: FuelPriceSource,
    val priceEffectiveDateEpochDay: Long?,
    val calculationSources: List<FuelCalculationField>,
    val calculatedField: FuelCalculationField?,
    val calculationOverflowField: FuelFormField?,
    val odometerOrderReason: String = "",
) {
    fun toUiState() = FuelFormUiState(
        fuelRecordId = sourceId,
        selectedVehicleId = selectedVehicleId,
        date = date,
        time = time,
        odometerKm = odometerKm,
        volumeLiters = volumeLiters,
        pricePerLiter = pricePerLiter,
        totalCostTwd = totalCostTwd,
        isFullTank = isFullTank,
        fuelProduct = fuelProduct,
        fuelingMode = fuelingMode,
        fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
        note = note,
        odometerOrderReason = odometerOrderReason,
        priceEffectiveDateEpochDay = priceEffectiveDateEpochDay,
        calculationSources = calculationSources,
        calculatedField = calculatedField,
        calculationOverflowField = calculationOverflowField,
        priceSource = priceSource.takeUnless { it == FuelPriceSource.LOADING } ?: FuelPriceSource.NONE,
    )
}

internal fun FuelFormUiState.toDraft(draftId: String, sourcePublicId: String?) = FuelFormDraft(
    draftId = draftId,
    sourceId = fuelRecordId,
    sourcePublicId = sourcePublicId,
    selectedVehiclePublicId = selectedVehicle?.publicId,
    selectedVehicleId = selectedVehicleId,
    date = date,
    time = time,
    odometerKm = odometerKm,
    volumeLiters = volumeLiters,
    pricePerLiter = pricePerLiter,
    totalCostTwd = totalCostTwd,
    isFullTank = isFullTank,
    fuelProduct = fuelProduct,
    fuelingMode = fuelingMode,
    fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
    note = note,
    odometerOrderReason = odometerOrderReason,
    priceSource = priceSource,
    priceEffectiveDateEpochDay = priceEffectiveDateEpochDay,
    calculationSources = calculationSources,
    calculatedField = calculatedField,
    calculationOverflowField = calculationOverflowField,
)
