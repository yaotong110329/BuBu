package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreStatutoryReminderSettings(
    private val preferences: DataStore<Preferences>,
) : StatutoryReminderSettings {
    override fun observeTaxAndFeeEnabled(): Flow<Boolean> =
        preferences.data.map { values -> values[TAX_AND_FEE_ENABLED] ?: true }

    override suspend fun setTaxAndFeeEnabled(enabled: Boolean) {
        preferences.edit { values -> values[TAX_AND_FEE_ENABLED] = enabled }
    }

    private companion object {
        val TAX_AND_FEE_ENABLED = booleanPreferencesKey("statutory_tax_and_fee_reminders_enabled")
    }
}
