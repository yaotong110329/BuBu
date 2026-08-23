package com.kumo.bubu.feature.fuel

import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelFormStateTest {
    private val today = LocalDate.of(2026, 8, 2)

    @Test
    fun litersAreParsedAsExactMlWithoutDouble() {
        val validation = validState(volumeLiters = "4.270").validate(today)

        assertTrue(validation.errors.isEmpty())
        assertEquals(4_270L, validation.input?.fuelVolumeMl)
    }

    @Test
    fun fractionalMilliPrecisionIsRejected() {
        val validation = validState(volumeLiters = "4.2701").validate(today)

        assertEquals(FuelFormError.POSITIVE_VOLUME_REQUIRED, validation.errors[FuelFormField.VOLUME])
    }

    @Test
    fun negativePriceIsReportedAsAFieldError() {
        val validation = validState().copy(pricePerLiter = "-31.7").validate(today)

        assertEquals(FuelFormError.INVALID_PRICE, validation.errors[FuelFormField.PRICE])
    }

    @Test
    fun volumeOver200LitersShowsAWarningWithoutBlockingTheValidRecord() {
        val state = validState(volumeLiters = "200.001")

        assertTrue(state.hasHighVolumeWarning)
        assertTrue(state.validate(today).errors.isEmpty())
    }

    @Test
    fun timeIsOptionalButMustUse24HourFormatWhenPresent() {
        val validation = validState(time = "25:00").validate(today)

        assertEquals(FuelFormError.INVALID_TIME, validation.errors[FuelFormField.TIME])
    }

    @Test
    fun lastTwoExplicitFieldsCalculateTheThirdField() {
        val state = validState(volumeLiters = "")
            .withUserValue(FuelCalculationField.PRICE, "31.7")
            .withUserValue(FuelCalculationField.TOTAL_COST, "135")

        assertEquals(FuelCalculationField.VOLUME, state.calculatedField)
        assertEquals("4.259", state.volumeLiters)
    }

    @Test
    fun calculatedFieldBecomesExplicitWhenUserOverridesIt() {
        val calculated = validState(volumeLiters = "")
            .withUserValue(FuelCalculationField.PRICE, "31.7")
            .withUserValue(FuelCalculationField.TOTAL_COST, "135")
        val overridden = calculated.withUserValue(FuelCalculationField.VOLUME, "4.270")

        assertEquals(FuelCalculationField.PRICE, overridden.calculatedField)
        assertEquals("31.616", overridden.pricePerLiter)
    }

    private fun validState(
        volumeLiters: String = "4.270",
        time: String = "",
    ) = FuelFormUiState(
        activeVehicles = listOf(vehicle()),
        selectedVehicleId = 3,
        date = today.toString(),
        time = time,
        odometerKm = "1234",
        volumeLiters = volumeLiters,
        totalCostTwd = "135",
    )

    private fun vehicle() = Vehicle(
        id = 3,
        publicId = "vehicle-public-id",
        name = "家用車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )
}
