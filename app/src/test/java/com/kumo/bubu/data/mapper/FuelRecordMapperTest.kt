package com.kumo.bubu.data.mapper

import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelRecordInput
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelRecordMapperTest {
    @Test
    fun newEntityKeepsMlAndTwdWithoutFloatingPointConversion() {
        val entity = input().toNewEntity("fuel-public-id", sequenceInDay = 2, nowEpochMillis = 9)

        assertEquals(4_270L, entity.fuelVolumeMl)
        assertEquals(31_700L, entity.pricePerLiterMilli)
        assertEquals(135L, entity.totalCostTwd)
        assertEquals(2, entity.sequenceInDay)
        assertEquals("加滿", entity.note)
    }

    @Test
    fun entityMapsToDomainWithoutChangingStableIdentity() {
        val record = input().toNewEntity("fuel-public-id", sequenceInDay = 2, nowEpochMillis = 9)
            .copy(id = 8)
            .toDomain()

        assertEquals(8L, record.id)
        assertEquals("fuel-public-id", record.publicId)
        assertEquals(FuelProduct.GASOLINE_95, record.fuelProduct)
    }

    @Test
    fun updatedEntityPreservesIdentityAndCreationTime() {
        val existing = input().toNewEntity("fuel-public-id", sequenceInDay = 2, nowEpochMillis = 9).copy(id = 8)
        val updated = input().copy(odometerKm = 1_500).toUpdatedEntity(existing, sequenceInDay = 3, nowEpochMillis = 10)

        assertEquals(8L, updated.id)
        assertEquals("fuel-public-id", updated.publicId)
        assertEquals(9L, updated.createdAt)
        assertEquals(10L, updated.updatedAt)
        assertEquals(1_500L, updated.odometerKm)
    }

    @Test
    fun mapsTheFuelEconomyStatisticsDecisionWithoutChangingFuelData() {
        val record = input()
            .copy(fuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.EXCLUDED)
            .toNewEntity("fuel-public-id", sequenceInDay = 2, nowEpochMillis = 9)
            .copy(id = 8)
            .toDomain()

        assertEquals(FuelEconomyStatisticsStatus.EXCLUDED, record.fuelEconomyStatisticsStatus)
        assertEquals(4_270L, record.fuelVolumeMl)
        assertEquals(135L, record.totalCostTwd)
    }

    private fun input() = FuelRecordInput(
        vehicleId = 3,
        dateEpochDay = 20_000,
        timeMinuteOfDay = 600,
        odometerKm = 1_234,
        fuelVolumeMl = 4_270,
        pricePerLiterMilli = 31_700,
        totalCostTwd = 135,
        isFullTank = true,
        fuelProduct = FuelProduct.GASOLINE_95,
        note = "加滿",
    )
}
