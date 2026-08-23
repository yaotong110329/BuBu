package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.kumo.bubu.domain.repository.BackupReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreBackupReminderSettings(
    private val preferences: DataStore<Preferences>,
) : BackupReminderSettings {
    override fun observeEnabled(): Flow<Boolean> = preferences.data.map { it[ENABLED] ?: false }

    override fun observeLastSuccessfulBackupEpochDay(): Flow<Long?> = preferences.data.map { it[LAST_SUCCESSFUL_BACKUP_EPOCH_DAY] }

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.edit { values -> values[ENABLED] = enabled }
    }

    override suspend fun recordSuccessfulBackup(epochDay: Long) {
        preferences.edit { values -> values[LAST_SUCCESSFUL_BACKUP_EPOCH_DAY] = epochDay }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("monthly_backup_reminder_enabled")
        val LAST_SUCCESSFUL_BACKUP_EPOCH_DAY = longPreferencesKey("last_successful_backup_epoch_day")
    }
}
