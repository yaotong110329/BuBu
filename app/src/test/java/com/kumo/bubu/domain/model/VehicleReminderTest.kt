package com.kumo.bubu.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleReminderTest {
    @Test
    fun statusUsesTheMoreSevereOfDateAndOdometer() {
        val reminder = reminder(dueOdometerKm = 12_000, dueDateEpochDay = LocalDate.of(2026, 8, 30).toEpochDay())

        assertEquals(ReminderStatus.DUE_SOON, reminder.status(11_800, LocalDate.of(2026, 8, 16)))
        assertEquals(ReminderStatus.OVERDUE, reminder.status(12_000, LocalDate.of(2026, 8, 16)))
        assertEquals(ReminderStatus.OVERDUE, reminder.status(11_000, LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun completedReminderHasNoActiveStatus() {
        assertNull(reminder(completedAt = 1L).status(20_000, LocalDate.of(2026, 8, 16)))
    }

    @Test
    fun manualInputRequiresTitleAndAtLeastOneDueTarget() {
        assertInvalid { ManualReminderInput(1, " ", 1_000, null).validated() }
        assertInvalid { ManualReminderInput(1, "驗車", null, null).validated() }
        assertEquals(
            "驗車",
            ManualReminderInput(1, " 驗車 ", null, 20_000).validated().title,
        )
    }

    private fun reminder(
        dueOdometerKm: Long? = null,
        dueDateEpochDay: Long? = null,
        completedAt: Long? = null,
    ) = VehicleReminder(
        id = 1,
        publicId = "reminder",
        vehicleId = 1,
        source = ReminderSource.MANUAL,
        sourceServiceItemId = null,
        title = "機油",
        dueOdometerKm = dueOdometerKm,
        dueDateEpochDay = dueDateEpochDay,
        completedByServiceRecordId = null,
        completedAt = completedAt,
        snoozedUntilEpochDay = null,
        isEnabled = true,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid reminder input")
        } catch (_: IllegalArgumentException) {
        }
    }
}
