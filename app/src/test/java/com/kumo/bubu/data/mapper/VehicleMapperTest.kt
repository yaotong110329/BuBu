package com.kumo.bubu.data.mapper

import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleMapperTest {
    @Test
    fun newVehicleNormalizesFieldsAndUsesTrackingOdometerAsCurrent() {
        val entity = completeInput().toNewEntity(publicId = "stable-id", nowEpochMillis = 999)

        assertEquals("通勤機車", entity.name)
        assertEquals(12_345, entity.currentOdometerKm)
        assertEquals("stable-id", entity.publicId)
        assertEquals(999, entity.createdAt)
        assertEquals(999, entity.updatedAt)
        assertFalse(entity.isArchived)
    }

    @Test
    fun optionalWhitespaceIsStoredAsNull() {
        val entity = completeInput().copy(brand = "   ").toNewEntity("stable-id", 999)

        assertNull(entity.brand)
    }

    @Test
    fun newCarCannotStoreMotorcycleClass() {
        val entity = completeInput().copy(
            vehicleType = VehicleType.CAR,
            motorcycleClass = MotorcycleClass.LIGHT,
        ).toNewEntity("stable-id", 999)

        assertNull(entity.motorcycleClass)
    }

    @Test
    fun updatePreservesIdentityAndCreationTime() {
        val existing = entity().copy(publicId = "never-change", createdAt = 100, isArchived = true)

        val updated = completeInput().copy(name = "新名稱", trackingStartOdometerKm = 88)
            .toUpdatedEntity(existing, nowEpochMillis = 200)

        assertEquals(existing.id, updated.id)
        assertEquals("never-change", updated.publicId)
        assertEquals(100, updated.createdAt)
        assertEquals(200, updated.updatedAt)
        assertEquals(88, updated.currentOdometerKm)
        assertEquals(true, updated.isArchived)
    }

    private fun completeInput() = VehicleInput(
        name = "  通勤機車  ",
        vehicleType = VehicleType.MOTORCYCLE,
        motorcycleClass = MotorcycleClass.ORDINARY_HEAVY,
        brand = "Kymco",
        model = "Like",
        manufactureYear = 2024,
        engineDisplacementCc = 125,
        licensePlate = "ABC-1234",
        powertrainType = PowertrainType.GASOLINE,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 12_345,
        note = "日常使用",
    )

    private fun entity() = VehicleEntity(
        id = 7,
        publicId = "old",
        name = "舊名稱",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 19_000,
        trackingStartOdometerKm = 1,
        currentOdometerKm = 1,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )
}
