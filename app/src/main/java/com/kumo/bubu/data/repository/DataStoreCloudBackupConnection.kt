package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DataStoreCloudBackupConnection(
    private val preferences: DataStore<Preferences>,
) {
    fun observe(): Flow<CloudBackupConnection> = preferences.data.map { stored ->
        if (stored[NEEDS_AUTHORIZATION] == true) {
            CloudBackupConnection.NeedsAuthorization
        } else stored[ACCOUNT_EMAIL]?.let { email ->
            CloudBackupConnection.Connected(
                CloudBackupAccount(email, stored[LAST_BACKUP_AT]),
            )
        } ?: CloudBackupConnection.NotConnected
    }

    suspend fun save(account: CloudBackupAccount) {
        preferences.edit { stored ->
            stored[ACCOUNT_EMAIL] = account.email
            stored.remove(NEEDS_AUTHORIZATION)
            account.lastCloudBackupAtEpochMillis?.let { stored[LAST_BACKUP_AT] = it }
                ?: stored.remove(LAST_BACKUP_AT)
        }
    }

    suspend fun updateLastBackupAt(epochMillis: Long) {
        preferences.edit { stored -> stored[LAST_BACKUP_AT] = epochMillis }
    }

    suspend fun markNeedsAuthorization() {
        preferences.edit { stored -> stored[NEEDS_AUTHORIZATION] = true }
    }

    suspend fun clear() {
        preferences.edit { stored ->
            stored.remove(ACCOUNT_EMAIL)
            stored.remove(LAST_BACKUP_AT)
            stored.remove(NEEDS_AUTHORIZATION)
        }
    }

    private companion object {
        val ACCOUNT_EMAIL = stringPreferencesKey("cloud_backup_account_email")
        val LAST_BACKUP_AT = longPreferencesKey("cloud_backup_last_backup_at")
        val NEEDS_AUTHORIZATION = booleanPreferencesKey("cloud_backup_needs_authorization")
    }
}
