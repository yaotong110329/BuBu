package com.kumo.bubu.data.mapper

import com.kumo.bubu.data.local.entity.ExpenseRecordEntity
import com.kumo.bubu.domain.model.ExpenseRecord
import com.kumo.bubu.domain.model.ExpenseRecordInput

fun ExpenseRecordEntity.toDomain() = ExpenseRecord(id, publicId, vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, category, totalCostTwd, note, createdAt, updatedAt, completedReminderId)

fun ExpenseRecordInput.toNewEntity(publicId: String, sequence: Int, now: Long, completedReminderId: Long? = null) = ExpenseRecordEntity(
    publicId = publicId, vehicleId = vehicleId, dateEpochDay = dateEpochDay, timeMinuteOfDay = timeMinuteOfDay,
    sequenceInDay = sequence, category = category, totalCostTwd = totalCostTwd, note = note, createdAt = now, updatedAt = now,
    completedReminderId = completedReminderId,
)

fun ExpenseRecordInput.toUpdatedEntity(existing: ExpenseRecordEntity, sequence: Int, now: Long) = existing.copy(
    vehicleId = vehicleId, dateEpochDay = dateEpochDay, timeMinuteOfDay = timeMinuteOfDay, sequenceInDay = sequence,
    category = category, totalCostTwd = totalCostTwd, note = note, updatedAt = now,
)
