package com.kumo.bubu.domain.repository

import kotlinx.coroutines.flow.Flow

interface ReminderNotificationSettings {
    fun observeEnabled(): Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}
