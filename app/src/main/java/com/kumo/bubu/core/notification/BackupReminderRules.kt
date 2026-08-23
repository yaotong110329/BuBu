package com.kumo.bubu.core.notification

import java.time.LocalDate

internal fun needsMonthlyBackupReminder(
    lastSuccessfulBackupEpochDay: Long?,
    today: LocalDate,
): Boolean {
    val lastBackup = lastSuccessfulBackupEpochDay?.let(LocalDate::ofEpochDay) ?: return true
    return lastBackup.year != today.year || lastBackup.month != today.month
}
