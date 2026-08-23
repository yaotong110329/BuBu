package com.kumo.bubu.core.notification

import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationRulesTest {
    @Test
    fun sendsEachNonNormalStatusOnlyOnceButSendsAgainWhenItEscalates() {
        assertTrue(
            shouldPostReminderNotification(
                status = ReminderStatus.DUE_SOON,
                lastNotifiedStatus = null,
                isArchived = false,
                isSnoozed = false,
            ),
        )
        assertFalse(
            shouldPostReminderNotification(
                status = ReminderStatus.DUE_SOON,
                lastNotifiedStatus = ReminderStatus.DUE_SOON,
                isArchived = false,
                isSnoozed = false,
            ),
        )
        assertTrue(
            shouldPostReminderNotification(
                status = ReminderStatus.OVERDUE,
                lastNotifiedStatus = ReminderStatus.DUE_SOON,
                isArchived = false,
                isSnoozed = false,
            ),
        )
    }

    @Test
    fun skipsNormalSnoozedAndArchivedReminders() {
        assertFalse(shouldPostReminderNotification(ReminderStatus.NORMAL, null, false, false))
        assertFalse(shouldPostReminderNotification(ReminderStatus.OVERDUE, null, false, true))
        assertFalse(shouldPostReminderNotification(ReminderStatus.OVERDUE, null, true, false))
    }

    @Test
    fun statutoryStagesAndMileageForecastAreDeduplicatedWithoutChangingReminderStatus() {
        val preview = selectReminderNotificationTrigger(
            source = ReminderSource.LICENSE_TAX,
            status = ReminderStatus.NORMAL,
            dueDateEpochDay = LocalDate.of(2026, 4, 30).toEpochDay(),
            referenceDateEpochDay = null,
            estimatedNotificationEpochDay = null,
            lastNotifiedTrigger = null,
            today = LocalDate.of(2026, 3, 25),
            isEnabled = true,
            isCompleted = false,
            isArchived = false,
            isSnoozed = false,
        )
        assertEquals("statutory:license-tax:preview", preview?.key)
        assertNull(
            selectReminderNotificationTrigger(
                source = ReminderSource.LICENSE_TAX,
                status = ReminderStatus.NORMAL,
                dueDateEpochDay = LocalDate.of(2026, 4, 30).toEpochDay(),
                referenceDateEpochDay = null,
                estimatedNotificationEpochDay = null,
                lastNotifiedTrigger = preview?.key,
                today = LocalDate.of(2026, 3, 25),
                isEnabled = true,
                isCompleted = false,
                isArchived = false,
                isSnoozed = false,
            ),
        )

        val forecast = selectReminderNotificationTrigger(
            source = ReminderSource.SERVICE_ITEM,
            status = ReminderStatus.NORMAL,
            dueDateEpochDay = null,
            referenceDateEpochDay = null,
            estimatedNotificationEpochDay = LocalDate.of(2026, 8, 16).toEpochDay(),
            lastNotifiedTrigger = null,
            today = LocalDate.of(2026, 8, 16),
            isEnabled = true,
            isCompleted = false,
            isArchived = false,
            isSnoozed = false,
        )
        assertEquals("mileage-forecast", forecast?.key)
        assertEquals(ReminderStatus.DUE_SOON, forecast?.notificationStatus)
        assertNull(
            selectReminderNotificationTrigger(
                source = ReminderSource.SERVICE_ITEM,
                status = ReminderStatus.DUE_SOON,
                dueDateEpochDay = null,
                referenceDateEpochDay = null,
                estimatedNotificationEpochDay = LocalDate.of(2026, 8, 16).toEpochDay(),
                lastNotifiedTrigger = forecast?.key,
                today = LocalDate.of(2026, 8, 17),
                isEnabled = true,
                isCompleted = false,
                isArchived = false,
                isSnoozed = false,
            ),
        )
        assertEquals(
            "status:OVERDUE",
            selectReminderNotificationTrigger(
                source = ReminderSource.SERVICE_ITEM,
                status = ReminderStatus.OVERDUE,
                dueDateEpochDay = null,
                referenceDateEpochDay = null,
                estimatedNotificationEpochDay = LocalDate.of(2026, 8, 16).toEpochDay(),
                lastNotifiedTrigger = forecast?.key,
                today = LocalDate.of(2026, 8, 18),
                isEnabled = true,
                isCompleted = false,
                isArchived = false,
                isSnoozed = false,
            )?.key,
        )
    }

    @Test
    fun laterStatutoryStageCanNotifyButDisabledCompletedArchivedAndSnoozedCannot() {
        val taxOpen = selectReminderNotificationTrigger(
            source = ReminderSource.LICENSE_TAX,
            status = ReminderStatus.NORMAL,
            dueDateEpochDay = LocalDate.of(2026, 4, 30).toEpochDay(),
            referenceDateEpochDay = null,
            estimatedNotificationEpochDay = null,
            lastNotifiedTrigger = "statutory:license-tax:preview",
            today = LocalDate.of(2026, 4, 1),
            isEnabled = true,
            isCompleted = false,
            isArchived = false,
            isSnoozed = false,
        )
        assertEquals("statutory:license-tax:open", taxOpen?.key)
        assertEquals(
            "status:OVERDUE",
            selectReminderNotificationTrigger(
                source = ReminderSource.LICENSE_TAX,
                status = ReminderStatus.OVERDUE,
                dueDateEpochDay = LocalDate.of(2026, 4, 30).toEpochDay(),
                referenceDateEpochDay = null,
                estimatedNotificationEpochDay = null,
                lastNotifiedTrigger = "statutory:license-tax:follow-up",
                today = LocalDate.of(2026, 5, 1),
                isEnabled = true,
                isCompleted = false,
                isArchived = false,
                isSnoozed = false,
            )?.key,
        )

        listOf(
            listOf(false, false, false, false),
            listOf(true, true, false, false),
            listOf(true, false, true, false),
            listOf(true, false, false, true),
        ).forEach { flags ->
            assertNull(
                selectReminderNotificationTrigger(
                    source = ReminderSource.MANUAL,
                    status = ReminderStatus.OVERDUE,
                    dueDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                    referenceDateEpochDay = null,
                    estimatedNotificationEpochDay = null,
                    lastNotifiedTrigger = null,
                    today = LocalDate.of(2026, 8, 16),
                    isEnabled = flags[0],
                    isCompleted = flags[1],
                    isArchived = flags[2],
                    isSnoozed = flags[3],
                ),
            )
        }
    }
}
