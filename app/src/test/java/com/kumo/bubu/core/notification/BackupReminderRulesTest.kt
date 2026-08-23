package com.kumo.bubu.core.notification

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderRulesTest {
    @Test
    fun requestsBackupWhenNoSuccessfulBackupHasBeenRecorded() {
        assertTrue(needsMonthlyBackupReminder(null, LocalDate.of(2026, 8, 21)))
    }

    @Test
    fun doesNotRequestBackupAgainInSameCalendarMonth() {
        assertFalse(
            needsMonthlyBackupReminder(
                LocalDate.of(2026, 8, 1).toEpochDay(),
                LocalDate.of(2026, 8, 31),
            ),
        )
    }

    @Test
    fun requestsBackupWhenCalendarMonthChanges() {
        assertTrue(
            needsMonthlyBackupReminder(
                LocalDate.of(2026, 12, 31).toEpochDay(),
                LocalDate.of(2027, 1, 1),
            ),
        )
    }
}
