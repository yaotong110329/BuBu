package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.VehicleReminder
import com.kumo.bubu.domain.model.ReminderSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ReminderRepository {
    fun observeReminders(): Flow<List<VehicleReminder>>

    suspend fun createManualReminder(input: ManualReminderInput): Long

    suspend fun completeReminder(id: Long)

    suspend fun snoozeReminder(id: Long, untilEpochDay: Long)

    suspend fun setReminderEnabled(id: Long, enabled: Boolean)

    suspend fun setTaxAndFeeRemindersEnabled(enabled: Boolean)

    suspend fun ensureStatutoryReminder(vehicleId: Long, cycleYear: Int, source: ReminderSource)

    suspend fun refreshAutomaticReminders(today: LocalDate)
}
