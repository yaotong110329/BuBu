package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kumo.bubu.domain.repository.MaintenanceSummaryStateStore
import com.kumo.bubu.domain.repository.MaintenanceSummaryNotificationState
import kotlinx.coroutines.flow.first

class DataStoreMaintenanceSummaryStateStore(
    private val preferences: DataStore<Preferences>,
) : MaintenanceSummaryStateStore {
    override suspend fun read(): MaintenanceSummaryNotificationState = preferences.data.first().let { values ->
        MaintenanceSummaryNotificationState(values[ALERTED_SIGNATURE], values[RENDERED_SIGNATURE])
    }

    override suspend fun write(state: MaintenanceSummaryNotificationState) {
        preferences.edit { values ->
            if (state.alertedSignature == null) values.remove(ALERTED_SIGNATURE)
            else values[ALERTED_SIGNATURE] = state.alertedSignature
            if (state.renderedSignature == null) values.remove(RENDERED_SIGNATURE)
            else values[RENDERED_SIGNATURE] = state.renderedSignature
        }
    }

    private companion object {
        val ALERTED_SIGNATURE = stringPreferencesKey("maintenance_summary_last_notified_signature")
        val RENDERED_SIGNATURE = stringPreferencesKey("maintenance_summary_last_rendered_signature")
    }
}
