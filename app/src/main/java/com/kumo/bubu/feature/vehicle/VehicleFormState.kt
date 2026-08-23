package com.kumo.bubu.feature.vehicle

import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType
import java.time.LocalDate
import java.time.MonthDay
import java.util.Locale

data class VehicleFormUiState(
    val vehicleId: Long? = null,
    val name: String = "",
    val vehicleType: VehicleType = VehicleType.CAR,
    val motorcycleClass: MotorcycleClass? = null,
    val brand: String = "",
    val model: String = "",
    val manufactureYear: String = "",
    val engineDisplacementCc: String = "",
    val licensePlate: String = "",
    val powertrainType: PowertrainType? = null,
    val trackingStartDate: String = LocalDate.now().toString(),
    val trackingStartOdometerKm: String = "0",
    val note: String = "",
    val primaryInspectionMonthDay: String = "",
    val secondaryInspectionMonthDay: String = "",
    val errors: Map<VehicleFormField, VehicleFormError> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isEditing: Boolean get() = vehicleId != null
}

enum class VehicleFormField {
    NAME,
    MOTORCYCLE_CLASS,
    MANUFACTURE_YEAR,
    ENGINE_DISPLACEMENT,
    TRACKING_START_DATE,
    TRACKING_START_ODOMETER,
    PRIMARY_INSPECTION_MONTH_DAY,
    SECONDARY_INSPECTION_MONTH_DAY,
}

enum class VehicleFormError {
    REQUIRED,
    INVALID_DATE,
    FUTURE_DATE,
    INVALID_YEAR,
    POSITIVE_INTEGER_REQUIRED,
    NON_NEGATIVE_INTEGER_REQUIRED,
    INVALID_MONTH_DAY,
}

sealed interface VehicleFormEvent {
    data class NameChanged(val value: String) : VehicleFormEvent
    data class VehicleTypeChanged(val value: VehicleType) : VehicleFormEvent
    data class MotorcycleClassChanged(val value: MotorcycleClass) : VehicleFormEvent
    data class BrandChanged(val value: String) : VehicleFormEvent
    data class ModelChanged(val value: String) : VehicleFormEvent
    data class ManufactureYearChanged(val value: String) : VehicleFormEvent
    data class EngineDisplacementChanged(val value: String) : VehicleFormEvent
    data class LicensePlateChanged(val value: String) : VehicleFormEvent
    data class PowertrainTypeChanged(val value: PowertrainType?) : VehicleFormEvent
    data class TrackingStartDateChanged(val value: String) : VehicleFormEvent
    data class TrackingStartOdometerChanged(val value: String) : VehicleFormEvent
    data class NoteChanged(val value: String) : VehicleFormEvent
    data class PrimaryInspectionMonthDayChanged(val value: String) : VehicleFormEvent
    data class SecondaryInspectionMonthDayChanged(val value: String) : VehicleFormEvent
    data object Save : VehicleFormEvent
}

sealed interface VehicleFormEffect {
    data object Saved : VehicleFormEffect
}

data class VehicleFormValidation(
    val input: VehicleInput?,
    val errors: Map<VehicleFormField, VehicleFormError>,
)

fun VehicleFormUiState.validate(today: LocalDate = LocalDate.now()): VehicleFormValidation {
    val startDate = trackingStartDate.trim().toLocalDateOrNull()
    val year = manufactureYear.trim().toOptionalInt()
    val displacement = engineDisplacementCc.trim().toOptionalInt()
    val odometer = trackingStartOdometerKm.trim().toLongOrNull()
    val primaryInspection = primaryInspectionMonthDay.toMonthDayOrNull()
    val secondaryInspection = secondaryInspectionMonthDay.toMonthDayOrNull()

    val validationErrors = buildMap {
        if (name.isBlank()) put(VehicleFormField.NAME, VehicleFormError.REQUIRED)
        if (vehicleType == VehicleType.MOTORCYCLE && motorcycleClass == null) {
            put(VehicleFormField.MOTORCYCLE_CLASS, VehicleFormError.REQUIRED)
        }
        when {
            startDate == null -> put(VehicleFormField.TRACKING_START_DATE, VehicleFormError.INVALID_DATE)
            startDate.isAfter(today) -> put(VehicleFormField.TRACKING_START_DATE, VehicleFormError.FUTURE_DATE)
        }
        if (manufactureYear.isNotBlank() && (year == null || year !in 1886..today.year)) {
            put(VehicleFormField.MANUFACTURE_YEAR, VehicleFormError.INVALID_YEAR)
        }
        if (engineDisplacementCc.isNotBlank() && (displacement == null || displacement <= 0)) {
            put(VehicleFormField.ENGINE_DISPLACEMENT, VehicleFormError.POSITIVE_INTEGER_REQUIRED)
        }
        if (odometer == null || odometer < 0) {
            put(VehicleFormField.TRACKING_START_ODOMETER, VehicleFormError.NON_NEGATIVE_INTEGER_REQUIRED)
        }
        if (primaryInspectionMonthDay.isNotBlank() && primaryInspection == null) {
            put(VehicleFormField.PRIMARY_INSPECTION_MONTH_DAY, VehicleFormError.INVALID_MONTH_DAY)
        }
        if (secondaryInspectionMonthDay.isNotBlank() && secondaryInspection == null) {
            put(VehicleFormField.SECONDARY_INSPECTION_MONTH_DAY, VehicleFormError.INVALID_MONTH_DAY)
        }
    }

    if (validationErrors.isNotEmpty()) return VehicleFormValidation(null, validationErrors)

    return VehicleFormValidation(
        input = VehicleInput(
            name = name.trim(),
            vehicleType = vehicleType,
            motorcycleClass = motorcycleClass.takeIf { vehicleType == VehicleType.MOTORCYCLE },
            brand = brand,
            model = model,
            manufactureYear = year,
            engineDisplacementCc = displacement,
            licensePlate = licensePlate,
            powertrainType = powertrainType,
            trackingStartDateEpochDay = requireNotNull(startDate).toEpochDay(),
            trackingStartOdometerKm = requireNotNull(odometer),
            note = note,
            primaryInspectionMonthDay = primaryInspection,
            secondaryInspectionMonthDay = secondaryInspection,
        ),
        errors = emptyMap(),
    )
}

fun Vehicle.toFormUiState(): VehicleFormUiState = VehicleFormUiState(
    vehicleId = id,
    name = name,
    vehicleType = vehicleType,
    motorcycleClass = motorcycleClass,
    brand = brand.orEmpty(),
    model = model.orEmpty(),
    manufactureYear = manufactureYear?.toString().orEmpty(),
    engineDisplacementCc = engineDisplacementCc?.toString().orEmpty(),
    licensePlate = licensePlate.orEmpty(),
    powertrainType = powertrainType,
    trackingStartDate = LocalDate.ofEpochDay(trackingStartDateEpochDay).toString(),
    trackingStartOdometerKm = trackingStartOdometerKm.toString(),
    note = note.orEmpty(),
    primaryInspectionMonthDay = primaryInspectionMonthDay?.toInputText().orEmpty(),
    secondaryInspectionMonthDay = secondaryInspectionMonthDay?.toInputText().orEmpty(),
)

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.toOptionalInt(): Int? = if (isBlank()) null else toIntOrNull()

private fun String.toMonthDayOrNull(): MonthDay? =
    trim().takeIf(String::isNotEmpty)?.let { value -> runCatching { MonthDay.parse("--$value") }.getOrNull() }

private fun MonthDay.toInputText(): String = "%02d-%02d".format(Locale.ROOT, monthValue, dayOfMonth)
