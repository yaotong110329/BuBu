package com.kumo.bubu.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRecordInputValidationTest {
    @Test fun futureDateIsRejected() {
        val input = input().copy(dateEpochDay = LocalDate.of(2026, 8, 4).toEpochDay())
        runCatching { input.validated(LocalDate.of(2026, 8, 3)) }.onSuccess { throw AssertionError("Expected failure") }
    }
    @Test fun blankItemNameIsRejected() {
        val input = input().copy(items = listOf(ServiceItemInput(" ", quantityMilli = 1_000, unitPriceTwd = 800)))
        runCatching { input.validated(LocalDate.of(2026, 8, 3)) }.onSuccess { throw AssertionError("Expected failure") }
    }
    @Test fun validInputKeepsIntegerTwdAndTrimsTitle() {
        val valid = input().validated(LocalDate.of(2026, 8, 3))

        assertEquals(800L, valid.totalCostTwd)
        assertEquals("定期保養", valid.title)
        assertEquals(PaymentMethod.CREDIT_CARD, valid.paymentMethod)
    }

    private fun input() = ServiceRecordInput(
        vehicleId = 3,
        dateEpochDay = LocalDate.of(2026, 8, 3).toEpochDay(),
        timeMinuteOfDay = null,
        odometerKm = 1_200,
        recordType = ServiceRecordType.MAINTENANCE,
        title = " 定期保養 ",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        totalCostTwd = 800,
        items = listOf(ServiceItemInput("機油", quantityMilli = 1_000, unitPriceTwd = 800)),
    )
}
