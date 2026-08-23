package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.kumo.bubu.domain.repository.ReminderNotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreReminderNotificationSettings(
    private val preferences: DataStore<Preferences>,
) : ReminderNotificationSettings {
    override fun observeEnabled(): Flow<Boolean> = preferences.data.map { it[ENABLED] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.edit { values -> values[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("reminder_notifications_enabled")
    }
}
