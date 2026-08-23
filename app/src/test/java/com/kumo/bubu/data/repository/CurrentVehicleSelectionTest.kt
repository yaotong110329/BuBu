package com.kumo.bubu.data.repository

import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentVehicleSelectionTest {
    @Test
    fun storedActiveVehicleRemainsCurrent() {
        val vehicles = listOf(vehicle("first"), vehicle("second"))

        assertEquals("second", resolveCurrentVehiclePublicId(vehicles, "second"))
    }

    @Test
    fun missingSelectionFallsBackToFirstActiveVehicle() {
        val vehicles = listOf(vehicle("archived", archived = true), vehicle("active"))

        assertEquals("active", resolveCurrentVehiclePublicId(vehicles, "missing"))
    }

    @Test
    fun noActiveVehicleClearsSelection() {
        assertNull(resolveCurrentVehiclePublicId(listOf(vehicle("archived", archived = true)), null))
    }

    private fun vehicle(publicId: String, archived: Boolean = false) = Vehicle(
        id = publicId.hashCode().toLong(),
        publicId = publicId,
        name = publicId,
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 0,
        currentOdometerKm = 0,
        note = null,
        isArchived = archived,
        createdAt = 1,
        updatedAt = 1,
    )
}
