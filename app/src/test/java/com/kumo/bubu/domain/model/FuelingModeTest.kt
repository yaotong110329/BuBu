package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelingModeTest {
    @Test
    fun fullServiceKeepsCpcListPrice() {
        assertEquals(28_900L, FuelingMode.FULL_SERVICE.applyToCpcListPrice(28_900L))
    }

    @Test
    fun selfServiceSubtractsTheSingleAuthoritativeDiscount() {
        assertEquals(28_100L, FuelingMode.SELF_SERVICE.applyToCpcListPrice(28_900L))
    }

    @Test
    fun selfServiceDiscountAppliesToEveryFuelProductPrice() {
        listOf(27_100L, 28_900L, 30_900L).forEach { listPrice ->
            assertEquals(listPrice - SELF_SERVICE_DISCOUNT_MILLI_TWD_PER_LITER, FuelingMode.SELF_SERVICE.applyToCpcListPrice(listPrice))
        }
    }
}
