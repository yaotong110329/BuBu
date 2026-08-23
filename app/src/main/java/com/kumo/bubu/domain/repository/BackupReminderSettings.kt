package com.kumo.bubu.domain.repository

import kotlinx.coroutines.flow.Flow

/** App preference for prompting the user to create a manual backup once per month. */
interface BackupReminderSettings {
    fun observeEnabled(): Flow<Boolean>

    fun observeLastSuccessfulBackupEpochDay(): Flow<Long?>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun recordSuccessfulBackup(epochDay: Long)
}
