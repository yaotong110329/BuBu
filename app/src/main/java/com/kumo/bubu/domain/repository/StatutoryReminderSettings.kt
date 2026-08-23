package com.kumo.bubu.domain.repository

import kotlinx.coroutines.flow.Flow

interface StatutoryReminderSettings {
    fun observeTaxAndFeeEnabled(): Flow<Boolean>

    suspend fun setTaxAndFeeEnabled(enabled: Boolean)
}
