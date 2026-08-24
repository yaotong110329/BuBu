package com.kumo.bubu.feature.fuel

import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelRecordInput
import com.kumo.bubu.domain.model.FuelEconomyOutlier
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.repository.FuelOdometerNeighbors
import com.kumo.bubu.domain.model.calculateFuelPricePerLiter
import com.kumo.bubu.domain.model.calculateFuelTotalCost
import com.kumo.bubu.domain.model.calculateFuelVolumeMl
import com.kumo.bubu.domain.model.toScaledDecimalText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

data class FuelFormUiState(
    val fuelRecordId: Long? = null,
    val activeVehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: Long? = null,
    val date: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().withSecond(0).withNano(0).toString(),
    val odometerKm: String = "",
    val volumeLiters: String = "",
    val pricePerLiter: String = "",
    val totalCostTwd: String = "",
    val isFullTank: Boolean = false,
    val fuelProduct: FuelProduct? = null,
    val fuelingMode: FuelingMode = FuelingMode.FULL_SERVICE,
    val fuelEconomyStatisticsStatus: FuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.UNREVIEWED,
    val note: String = "",
    val priceSource: FuelPriceSource = FuelPriceSource.NONE,
    val priceEffectiveDateEpochDay: Long? = null,
    val calculationSources: List<FuelCalculationField> = emptyList(),
    val calculatedField: FuelCalculationField? = null,
    val errors: Map<FuelFormField, FuelFormError> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val odometerOrderWarning: FuelOdometerNeighbors? = null,
    val odometerOrderReason: String = "",
    val odometerOrderReasonRequired: Boolean = false,
    val pendingSaveInput: FuelRecordInput? = null,
    val fuelEconomyOutlier: FuelEconomyOutlier? = null,
) {
    val canSave: Boolean get() = activeVehicles.isNotEmpty() && !isLoading && !isSaving
    val isEditing: Boolean get() = fuelRecordId != null
    val hasHighVolumeWarning: Boolean get() =
        volumeLiters.trim().toMillilitersOrNull()?.let { it > HIGH_VOLUME_WARNING_ML } == true

    val selectedVehicle: Vehicle? get() = activeVehicles.firstOrNull { it.id == selectedVehicleId }
}

enum class FuelPriceSource {
    NONE,
    LOADING,
    CPC_MANUAL,
    LAST_RECORD,
    CPC_FETCH_FAILED_LAST_RECORD,
    CPC_FETCH_FAILED,
    MANUAL,
    CALCULATED,
    UNAVAILABLE,
}

enum class FuelFormField {
    VEHICLE,
    DATE,
    TIME,
    ODOMETER,
    VOLUME,
    TOTAL_COST,
    PRICE,
}

enum class FuelCalculationField {
    VOLUME,
    PRICE,
    TOTAL_COST,
}

enum class FuelFormError {
    REQUIRED,
    INVALID_DATE,
    FUTURE_DATE,
    INVALID_TIME,
    NON_NEGATIVE_INTEGER_REQUIRED,
    POSITIVE_VOLUME_REQUIRED,
    INVALID_TOTAL_COST,
    INVALID_PRICE,
}

sealed interface FuelFormEvent {
    data class VehicleChanged(val vehicleId: Long) : FuelFormEvent
    data class DateChanged(val value: String) : FuelFormEvent
    data class TimeChanged(val value: String) : FuelFormEvent
    data class OdometerChanged(val value: String) : FuelFormEvent
    data class VolumeChanged(val value: String) : FuelFormEvent
    data class PriceChanged(val value: String) : FuelFormEvent
    data class TotalCostChanged(val value: String) : FuelFormEvent
    data class FullTankChanged(val value: Boolean) : FuelFormEvent
    data class FuelProductChanged(val value: FuelProduct?) : FuelFormEvent
    data class FuelingModeChanged(val value: FuelingMode) : FuelFormEvent
    data class FuelEconomyStatisticsStatusChanged(val value: FuelEconomyStatisticsStatus) : FuelFormEvent
    data object RefreshPrice : FuelFormEvent
    data class NoteChanged(val value: String) : FuelFormEvent
    data class OdometerOrderReasonChanged(val value: String) : FuelFormEvent
    data object ConfirmOdometerOrder : FuelFormEvent
    data object DismissOdometerOrder : FuelFormEvent
    data object ConfirmFuelEconomyOutlier : FuelFormEvent
    data object ExcludeFuelEconomyOutlier : FuelFormEvent
    data object DismissFuelEconomyOutlier : FuelFormEvent
    data object Save : FuelFormEvent
}

sealed interface FuelFormEffect {
    data object Saved : FuelFormEffect
}

data class FuelFormValidation(
    val input: FuelRecordInput?,
    val errors: Map<FuelFormField, FuelFormError>,
)

fun FuelFormUiState.validate(today: LocalDate = LocalDate.now()): FuelFormValidation {
    val parsedDate = date.trim().toLocalDateOrNull()
    val parsedTime = time.trim().toMinuteOfDayOrNull()
    val parsedOdometer = odometerKm.trim().toLongOrNull()
    val parsedVolume = volumeLiters.trim().toMillilitersOrNull()
    val parsedPrice = pricePerLiter.trim().toMilliTwdOrNull()
    val parsedTotal = totalCostTwd.trim().toLongOrNull()

    val errors = buildMap {
        if (selectedVehicleId == null) put(FuelFormField.VEHICLE, FuelFormError.REQUIRED)
        when {
            parsedDate == null -> put(FuelFormField.DATE, FuelFormError.INVALID_DATE)
            parsedDate.isAfter(today) -> put(FuelFormField.DATE, FuelFormError.FUTURE_DATE)
        }
        if (time.isNotBlank() && parsedTime == null) put(FuelFormField.TIME, FuelFormError.INVALID_TIME)
        if (parsedOdometer == null || parsedOdometer < 0) {
            put(FuelFormField.ODOMETER, FuelFormError.NON_NEGATIVE_INTEGER_REQUIRED)
        }
        if (parsedVolume == null || parsedVolume !in 1..999_999L) put(FuelFormField.VOLUME, FuelFormError.POSITIVE_VOLUME_REQUIRED)
        if (totalCostTwd.isBlank() || parsedTotal == null || parsedTotal < 0) {
            put(FuelFormField.TOTAL_COST, FuelFormError.INVALID_TOTAL_COST)
        }
        if (pricePerLiter.isNotBlank() && (parsedPrice == null || parsedPrice < 0)) {
            put(FuelFormField.PRICE, FuelFormError.INVALID_PRICE)
        }
    }
    if (errors.isNotEmpty()) return FuelFormValidation(null, errors)

    return FuelFormValidation(
        input = FuelRecordInput(
            vehicleId = requireNotNull(selectedVehicleId),
            dateEpochDay = requireNotNull(parsedDate).toEpochDay(),
            timeMinuteOfDay = parsedTime,
            odometerKm = requireNotNull(parsedOdometer),
            fuelVolumeMl = requireNotNull(parsedVolume),
            pricePerLiterMilli = parsedPrice,
            totalCostTwd = requireNotNull(parsedTotal),
            isFullTank = isFullTank,
            fuelProduct = fuelProduct,
            fuelingMode = fuelingMode,
            fuelEconomyStatisticsStatus = fuelEconomyStatisticsStatus,
            note = note,
        ),
        errors = emptyMap(),
    )
}

internal fun String.toMillilitersOrNull(): Long? = decimalToScaledLongOrNull(3)

internal fun String.toMilliTwdOrNull(): Long? = decimalToScaledLongOrNull(3)

private fun String.decimalToScaledLongOrNull(scale: Int): Long? = runCatching {
    BigDecimal(this).movePointRight(scale).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
}.getOrNull()

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.toMinuteOfDayOrNull(): Int? = runCatching {
    val time = LocalTime.parse(this)
    time.hour * 60 + time.minute
}.getOrNull()

fun FuelFormUiState.withUserValue(field: FuelCalculationField, value: String): FuelFormUiState {
    val sources = (calculationSources - field + field).takeLast(2)
    val changed = when (field) {
        FuelCalculationField.VOLUME -> copy(volumeLiters = value)
        FuelCalculationField.PRICE -> copy(pricePerLiter = value)
        FuelCalculationField.TOTAL_COST -> copy(totalCostTwd = value)
    }.copy(calculationSources = sources, calculatedField = null)
    return changed.withCalculatedValue()
}

fun FuelFormUiState.withUserCalculationValue(field: FuelCalculationField, value: String): FuelFormUiState {
    val updated = withUserValue(field, value)
    return if (updated.calculatedField == FuelCalculationField.PRICE) {
        updated.copy(priceSource = FuelPriceSource.CALCULATED, priceEffectiveDateEpochDay = null)
    } else {
        updated
    }
}

private fun FuelFormUiState.withCalculatedValue(): FuelFormUiState {
    if (calculationSources.size != 2) return this
    val volume = volumeLiters.trim().toMillilitersOrNull()
    val price = pricePerLiter.trim().toMilliTwdOrNull()
    val total = totalCostTwd.trim().toLongOrNull()
    return when (calculationSources.toSet()) {
        setOf(FuelCalculationField.VOLUME, FuelCalculationField.PRICE) -> {
            if (volume == null || price == null) this else copy(
                totalCostTwd = calculateFuelTotalCost(volume, price).toString(),
                calculatedField = FuelCalculationField.TOTAL_COST,
            )
        }
        setOf(FuelCalculationField.VOLUME, FuelCalculationField.TOTAL_COST) -> {
            if (volume == null || total == null) this else copy(
                pricePerLiter = calculateFuelPricePerLiter(volume, total)?.toScaledDecimalText(3).orEmpty(),
                calculatedField = FuelCalculationField.PRICE,
            )
        }
        setOf(FuelCalculationField.PRICE, FuelCalculationField.TOTAL_COST) -> {
            if (price == null || total == null) this else copy(
                volumeLiters = calculateFuelVolumeMl(price, total)?.toScaledDecimalText(3).orEmpty(),
                calculatedField = FuelCalculationField.VOLUME,
            )
        }
        else -> this
    }
}

private const val HIGH_VOLUME_WARNING_ML = 200_000L
