package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCalculationTest {
    @Test
    fun volumeAndPriceCalculateRoundedIntegerTwdTotal() {
        assertEquals(135L, calculateFuelTotalCost(fuelVolumeMl = 4_270L, pricePerLiterMilli = 31_700L))
    }

    @Test
    fun volumeAndTotalCalculateMilliTwdPrice() {
        assertEquals(31_616L, calculateFuelPricePerLiter(fuelVolumeMl = 4_270L, totalCostTwd = 135L))
    }

    @Test
    fun priceAndTotalCalculateMlVolume() {
        assertEquals(4_259L, calculateFuelVolumeMl(pricePerLiterMilli = 31_700L, totalCostTwd = 135L))
    }

    @Test
    fun zeroDivisorCannotProduceCalculatedFuelValue() {
        assertNull(calculateFuelPricePerLiter(fuelVolumeMl = 0, totalCostTwd = 135))
        assertNull(calculateFuelVolumeMl(pricePerLiterMilli = 0, totalCostTwd = 135))
    }
}
