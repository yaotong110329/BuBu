package com.kumo.bubu.data.repository

import androidx.room.withTransaction
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.dao.ExpenseRecordDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.local.dao.VehicleReminderDao
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.data.mapper.toNewEntity
import com.kumo.bubu.data.mapper.toUpdatedEntity
import com.kumo.bubu.domain.model.ExpenseRecord
import com.kumo.bubu.domain.model.ExpenseRecordInput
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.validated
import com.kumo.bubu.domain.repository.ExpenseRepository
import com.kumo.bubu.domain.repository.ReminderRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineExpenseRepository(
    private val database: BuBuDatabase,
    private val vehicleDao: VehicleDao,
    private val expenseRecordDao: ExpenseRecordDao,
    private val reminderDao: VehicleReminderDao,
    private val reminderRepository: ReminderRepository,
) : ExpenseRepository {
    override fun observeRecentExpenseRecords(): Flow<List<ExpenseRecord>> = expenseRecordDao.observeRecent().map { records -> records.map { it.toDomain() } }
    override suspend fun getExpenseRecord(id: Long): ExpenseRecord? = expenseRecordDao.getById(id)?.toDomain()
    override suspend fun createExpenseRecord(input: ExpenseRecordInput): Long {
        val valid = input.validated()
        if (valid.completeSameCycleReminder && valid.category.isStatutoryExpense()) {
            reminderRepository.ensureStatutoryReminder(
                valid.vehicleId,
                LocalDate.ofEpochDay(valid.dateEpochDay).year,
                valid.category.toReminderSource(),
            )
        }
        return database.withTransaction {
            val vehicle = requireNotNull(vehicleDao.getById(valid.vehicleId)) { "Vehicle does not exist." }
            require(!vehicle.isArchived) { "Archived vehicle cannot receive expenses." }
            val now = System.currentTimeMillis()
            val reminder = valid.findSameCycleReminder()?.takeIf { it.completedAt == null }
            val expenseId = expenseRecordDao.insert(
                valid.toNewEntity(
                    UUID.randomUUID().toString(),
                    expenseRecordDao.nextSequenceInDay(valid.vehicleId, valid.dateEpochDay),
                    now,
                    reminder?.id,
                ),
            )
            if (reminder != null) {
                reminderDao.update(
                    reminder.copy(
                        completedByServiceRecordId = null,
                        completedByExpenseRecordId = expenseId,
                        completedAt = now,
                        snoozedUntilEpochDay = null,
                        lastNotifiedStatus = null,
                        lastNotifiedTrigger = null,
                        updatedAt = now,
                    ),
                )
            }
            expenseId
        }
    }
    override suspend fun updateExpenseRecord(id: Long, input: ExpenseRecordInput) = database.withTransaction {
        val existing = requireNotNull(expenseRecordDao.getById(id)) { "Expense record does not exist." }
        val valid = input.validated()
        val target = requireNotNull(vehicleDao.getById(valid.vehicleId)) { "Vehicle does not exist." }
        require(!target.isArchived || target.id == existing.vehicleId) { "Archived vehicle cannot receive moved expenses." }
        val sequence = if (existing.vehicleId == valid.vehicleId && existing.dateEpochDay == valid.dateEpochDay) existing.sequenceInDay else expenseRecordDao.nextSequenceInDay(valid.vehicleId, valid.dateEpochDay)
        val keepsReminderLink = existing.vehicleId == valid.vehicleId &&
            existing.category == valid.category &&
            LocalDate.ofEpochDay(existing.dateEpochDay).year == LocalDate.ofEpochDay(valid.dateEpochDay).year
        if (!keepsReminderLink) clearExpenseCompletion(existing.id, existing.completedReminderId)
        expenseRecordDao.update(
            valid.toUpdatedEntity(existing, sequence, System.currentTimeMillis()).copy(
                completedReminderId = existing.completedReminderId.takeIf { keepsReminderLink },
            ),
        )
    }
    override suspend fun deleteExpenseRecord(id: Long) = database.withTransaction {
        val existing = expenseRecordDao.getById(id) ?: return@withTransaction
        clearExpenseCompletion(existing.id, existing.completedReminderId)
        expenseRecordDao.deleteById(id)
    }

    private suspend fun clearExpenseCompletion(expenseId: Long, reminderId: Long?) {
        val reminder = reminderId?.let { reminderDao.getById(it) } ?: return
        if (reminder.completedByExpenseRecordId != expenseId) return
        reminderDao.update(
            reminder.copy(
                completedByExpenseRecordId = null,
                completedAt = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun ExpenseRecordInput.findSameCycleReminder() = if (completeSameCycleReminder) {
        val kind = when (category) {
            ExpenseCategory.LICENSE_TAX -> "LICENSE_TAX"
            ExpenseCategory.ROAD_MAINTENANCE_FEE -> "ROAD_MAINTENANCE_FEE"
            else -> null
        }
        val year = LocalDate.ofEpochDay(dateEpochDay).year
        kind?.let { reminderDao.getByAutomaticKey("statutory:$vehicleId:$it:$year") }
    } else {
        null
    }

    private fun ExpenseCategory.isStatutoryExpense(): Boolean =
        this == ExpenseCategory.LICENSE_TAX || this == ExpenseCategory.ROAD_MAINTENANCE_FEE

    private fun ExpenseCategory.toReminderSource(): ReminderSource = when (this) {
        ExpenseCategory.LICENSE_TAX -> ReminderSource.LICENSE_TAX
        ExpenseCategory.ROAD_MAINTENANCE_FEE -> ReminderSource.ROAD_MAINTENANCE_FEE
        else -> error("Expense category does not have an automatic statutory reminder.")
    }
}
