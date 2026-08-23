package com.kumo.bubu.data.mapper

import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.domain.model.ServiceAttachment
import com.kumo.bubu.domain.model.ServiceAttachmentInput
import com.kumo.bubu.domain.model.ServiceItem
import com.kumo.bubu.domain.model.ServiceItemInput
import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceTypeInput
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import com.kumo.bubu.domain.model.VehicleReminder

fun ServiceRecordEntity.toDomain() = ServiceRecord(
    id = id,
    publicId = publicId,
    vehicleId = vehicleId,
    dateEpochDay = dateEpochDay,
    timeMinuteOfDay = timeMinuteOfDay,
    sequenceInDay = sequenceInDay,
    odometerKm = odometerKm,
    recordType = recordType,
    title = title,
    paymentMethod = paymentMethod,
    totalCostTwd = totalCostTwd,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ServiceItemEntity.toDomain() = ServiceItem(id, publicId, serviceRecordId, serviceTypeId, sequenceInRecord, nameSnapshot, quantityMilli, quantityUnit, unitPriceTwd, subtotalTwd, nextDueOdometerKm, nextDueDateEpochDay, note, createdAt, updatedAt)

fun ServiceAttachmentEntity.toDomain() = ServiceAttachment(
    id = id,
    publicId = publicId,
    serviceRecordId = serviceRecordId,
    sequenceInRecord = sequenceInRecord,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    createdAt = createdAt,
)

fun ServiceRecordInput.toNewEntity(publicId: String, sequence: Int, now: Long) = ServiceRecordEntity(
    publicId = publicId, vehicleId = vehicleId, dateEpochDay = dateEpochDay, timeMinuteOfDay = timeMinuteOfDay,
    sequenceInDay = sequence, odometerKm = odometerKm, recordType = recordType, title = title,
    paymentMethod = paymentMethod,
    totalCostTwd = totalCostTwd, note = note, createdAt = now, updatedAt = now,
)

fun ServiceItemInput.toNewEntity(serviceRecordId: Long, sequence: Int, publicId: String, now: Long) = ServiceItemEntity(
    publicId = publicId, serviceRecordId = serviceRecordId, serviceTypeId = serviceTypeId, sequenceInRecord = sequence, nameSnapshot = nameSnapshot,
    quantityMilli = quantityMilli, quantityUnit = quantityUnit, unitPriceTwd = unitPriceTwd, subtotalTwd = subtotalTwd,
    nextDueOdometerKm = nextDueOdometerKm, nextDueDateEpochDay = nextDueDateEpochDay, note = note,
    createdAt = now, updatedAt = now,
)

fun ServiceItemInput.toUpdatedEntity(existing: ServiceItemEntity, sequence: Int, now: Long) = existing.copy(
    serviceTypeId = serviceTypeId, sequenceInRecord = sequence, nameSnapshot = nameSnapshot,
    quantityMilli = quantityMilli, quantityUnit = quantityUnit, unitPriceTwd = unitPriceTwd,
    subtotalTwd = subtotalTwd, nextDueOdometerKm = nextDueOdometerKm,
    nextDueDateEpochDay = nextDueDateEpochDay, note = note, updatedAt = now,
)

fun ServiceAttachmentInput.toNewEntity(
    serviceRecordId: Long,
    sequence: Int,
    publicId: String,
    now: Long,
) = ServiceAttachmentEntity(
    publicId = publicId,
    serviceRecordId = serviceRecordId,
    sequenceInRecord = sequence,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    createdAt = now,
    updatedAt = now,
)

fun ServiceTypeEntity.toDomain() = ServiceType(id, publicId, name, isBuiltIn, isArchived, sortOrder, createdAt, updatedAt, vehicleType)

fun ServiceTypeInput.toNewEntity(publicId: String, sortOrder: Int, now: Long) = ServiceTypeEntity(
    publicId = publicId,
    name = name,
    vehicleType = vehicleType,
    isBuiltIn = false,
    isArchived = false,
    sortOrder = sortOrder,
    createdAt = now,
    updatedAt = now,
)

fun VehicleReminderEntity.toDomain() = VehicleReminder(
    id = id,
    publicId = publicId,
    vehicleId = vehicleId,
    source = source,
    sourceServiceItemId = sourceServiceItemId,
    title = title,
    dueOdometerKm = dueOdometerKm,
    dueDateEpochDay = dueDateEpochDay,
    completedByServiceRecordId = completedByServiceRecordId,
    completedAt = completedAt,
    snoozedUntilEpochDay = snoozedUntilEpochDay,
    lastNotifiedStatus = lastNotifiedStatus,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    automaticKey = automaticKey,
    ruleVersion = ruleVersion,
    ruleVerifiedEpochDay = ruleVerifiedEpochDay,
    estimatedNotificationEpochDay = estimatedNotificationEpochDay,
    lastNotifiedTrigger = lastNotifiedTrigger,
    referenceDateEpochDay = referenceDateEpochDay,
    completedByExpenseRecordId = completedByExpenseRecordId,
)
