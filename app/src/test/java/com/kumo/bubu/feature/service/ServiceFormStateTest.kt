package com.kumo.bubu.feature.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceFormStateTest {
    @Test
    fun itemAmountIsTheItemSubtotal() {
        val item = ServiceItemDraft(amount = "152")

        assertEquals(152L, item.amountTwd)
    }

    @Test
    fun invalidAmountDoesNotProduceAValue() {
        val item = ServiceItemDraft(amount = "-1")

        assertNull(item.amountTwd)
    }

    @Test
    fun workOrderCalculatedTotalIsTheSumOfItemAmountsOnly() {
        val state = ServiceFormUiState(
            items = listOf(
                ServiceItemDraft(name = "Oil", amount = "500"),
                ServiceItemDraft(name = "Filter", amount = "300"),
            ),
        )

        assertEquals(800L, state.calculatedItemsTotalTwd)
    }

    @Test
    fun workOrderTotalOverflowIsReportedAsInvalidInsteadOfSaturating() {
        val state = ServiceFormUiState(
            items = listOf(
                ServiceItemDraft(name = "A", amount = Long.MAX_VALUE.toString()),
                ServiceItemDraft(name = "B", amount = "1"),
            ),
        )

        assertNull(state.calculatedItemsTotalTwd)
    }

    @Test
    fun unfinishedItemDoesNotLookLikeAnOverflow() {
        val state = ServiceFormUiState(items = listOf(ServiceItemDraft(name = "Oil")))

        assertEquals(0L, state.calculatedItemsTotalTwd)
    }
}
