package com.kumo.bubu.feature.vehicle

import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleFormStateTest {
    private val today = LocalDate.of(2026, 8, 1)

    @Test
    fun blankNameCannotBeSaved() {
        val validation = validState(name = "   ").validate(today)
        assertNull(validation.input)
        assertEquals(VehicleFormError.REQUIRED, validation.errors[VehicleFormField.NAME])
    }

    @Test
    fun futureDateCannotBeSaved() {
        val validation = validState(trackingStartDate = "2026-08-02").validate(today)
        assertEquals(VehicleFormError.FUTURE_DATE, validation.errors[VehicleFormField.TRACKING_START_DATE])
    }

    @Test
    fun motorcycleRequiresClass() {
        val validation = validState(vehicleType = VehicleType.MOTORCYCLE).validate(today)
        assertEquals(VehicleFormError.REQUIRED, validation.errors[VehicleFormField.MOTORCYCLE_CLASS])
    }

    @Test
    fun optionalNumbersMustBeValidWhenPresent() {
        val validation = validState(manufactureYear = "2030", displacement = "0").validate(today)
        assertEquals(VehicleFormError.INVALID_YEAR, validation.errors[VehicleFormField.MANUFACTURE_YEAR])
        assertEquals(VehicleFormError.POSITIVE_INTEGER_REQUIRED, validation.errors[VehicleFormField.ENGINE_DISPLACEMENT])
    }

    @Test
    fun completeVehicleProducesDomainInput() {
        val validation = validState(
            vehicleType = VehicleType.MOTORCYCLE,
            motorcycleClass = MotorcycleClass.LIGHT,
            manufactureYear = "2025",
            displacement = "125",
        ).validate(today)

        assertTrue(validation.errors.isEmpty())
        assertEquals(MotorcycleClass.LIGHT, validation.input?.motorcycleClass)
        assertEquals(PowertrainType.GASOLINE, validation.input?.powertrainType)
        assertEquals(42L, validation.input?.trackingStartOdometerKm)
    }

    @Test
    fun carDropsMotorcycleClass() {
        val validation = validState(motorcycleClass = MotorcycleClass.LIGHT).validate(today)
        assertNull(validation.input?.motorcycleClass)
    }

    @Test
    fun unspecifiedPowertrainIsPreservedByValidation() {
        val validation = validState(powertrainType = null).validate(today)

        assertNull(validation.input?.powertrainType)
    }

    @Test
    fun existingVehicleWithUnspecifiedPowertrainKeepsNullInForm() {
        val state = Vehicle(
            id = 7,
            publicId = "vehicle-7",
            name = "家用車",
            vehicleType = VehicleType.CAR,
            motorcycleClass = null,
            brand = null,
            model = null,
            manufactureYear = null,
            engineDisplacementCc = null,
            licensePlate = null,
            powertrainType = null,
            trackingStartDateEpochDay = today.toEpochDay(),
            trackingStartOdometerKm = 42,
            currentOdometerKm = 42,
            note = null,
            isArchived = false,
            createdAt = 1,
            updatedAt = 1,
        ).toFormUiState()

        assertNull(state.powertrainType)
        assertNull(state.validate(today).input?.powertrainType)
    }

    @Test
    fun inspectionMonthDaysAreValidatedAndStoredSeparately() {
        val valid = validState(manufactureYear = "2010").copy(
            primaryInspectionMonthDay = "03-08",
            secondaryInspectionMonthDay = "09-12",
        ).validate(today)
        val invalid = validState(manufactureYear = "2010").copy(
            primaryInspectionMonthDay = "02-30",
        ).validate(today)

        assertEquals(MonthDay.of(3, 8), valid.input?.primaryInspectionMonthDay)
        assertEquals(MonthDay.of(9, 12), valid.input?.secondaryInspectionMonthDay)
        assertEquals(
            VehicleFormError.INVALID_MONTH_DAY,
            invalid.errors[VehicleFormField.PRIMARY_INSPECTION_MONTH_DAY],
        )
    }

    private fun validState(
        name: String = "家用車",
        vehicleType: VehicleType = VehicleType.CAR,
        motorcycleClass: MotorcycleClass? = null,
        trackingStartDate: String = "2026-08-01",
        manufactureYear: String = "",
        displacement: String = "",
        powertrainType: PowertrainType? = PowertrainType.GASOLINE,
    ) = VehicleFormUiState(
        name = name,
        vehicleType = vehicleType,
        motorcycleClass = motorcycleClass,
        manufactureYear = manufactureYear,
        engineDisplacementCc = displacement,
        powertrainType = powertrainType,
        trackingStartDate = trackingStartDate,
        trackingStartOdometerKm = "42",
    )
}
