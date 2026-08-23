package com.kumo.bubu.data.backup

import com.kumo.bubu.data.local.entity.ExpenseRecordEntity
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import kotlinx.serialization.Serializable

data class BackupDataSource(
    val vehicles: List<VehicleEntity>,
    val fuelRecords: List<FuelRecordEntity>,
    val serviceTypes: List<ServiceTypeEntity>,
    val serviceRecords: List<ServiceRecordEntity>,
    val serviceItems: List<ServiceItemEntity>,
    val expenseRecords: List<ExpenseRecordEntity>,
    val reminders: List<VehicleReminderEntity>,
    val attachments: List<ServiceAttachmentEntity>,
)

data class BackupSnapshot(
    val data: BackupData,
    val attachmentSources: List<BackupAttachmentSource>,
)

data class BackupAttachmentSource(
    val relativePath: String,
    val archivePath: String,
)

@Serializable
data class BackupData(
    val vehicles: List<BackupVehicle>,
    val fuelRecords: List<BackupFuelRecord>,
    val serviceTypes: List<BackupServiceType>,
    val serviceRecords: List<BackupServiceRecord>,
    val serviceItems: List<BackupServiceItem>,
    val expenseRecords: List<BackupExpenseRecord>,
    val reminders: List<BackupReminder>,
    val attachments: List<BackupAttachment>,
)

@Serializable
data class BackupVehicle(
    val publicId: String,
    val name: String,
    val vehicleType: String,
    val motorcycleClass: String?,
    val brand: String?,
    val model: String?,
    val manufactureYear: Int?,
    val engineDisplacementCc: Int?,
    val licensePlate: String?,
    val powertrainType: String?,
    val trackingStartDateEpochDay: Long,
    val trackingStartOdometerKm: Long,
    val currentOdometerKm: Long,
    val note: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val primaryInspectionMonthDay: String?,
    val secondaryInspectionMonthDay: String?,
)

@Serializable
data class BackupFuelRecord(
    val publicId: String,
    val vehiclePublicId: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val fuelVolumeMl: Long,
    val pricePerLiterMilli: Long?,
    val totalCostTwd: Long,
    val isFullTank: Boolean,
    val fuelProduct: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val fuelingMode: String = "FULL_SERVICE",
)

@Serializable
data class BackupServiceType(
    val publicId: String,
    val name: String,
    val vehicleType: String = "CAR",
    val isBuiltIn: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupServiceRecord(
    val publicId: String,
    val vehiclePublicId: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val recordType: String,
    val title: String,
    val paymentMethod: String?,
    val totalCostTwd: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupServiceItem(
    val publicId: String,
    val serviceRecordPublicId: String,
    val serviceTypePublicId: String?,
    val sequenceInRecord: Int,
    val nameSnapshot: String,
    val quantityMilli: Long,
    val quantityUnit: String,
    val unitPriceTwd: Long,
    val subtotalTwd: Long,
    val nextDueOdometerKm: Long?,
    val nextDueDateEpochDay: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupExpenseRecord(
    val publicId: String,
    val vehiclePublicId: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val category: String,
    val totalCostTwd: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedReminderPublicId: String?,
)

@Serializable
data class BackupReminder(
    val publicId: String,
    val vehiclePublicId: String,
    val source: String,
    val sourceServiceItemPublicId: String?,
    val title: String,
    val dueOdometerKm: Long?,
    val dueDateEpochDay: Long?,
    val completedByServiceRecordPublicId: String?,
    val completedAt: Long?,
    val snoozedUntilEpochDay: Long?,
    val lastNotifiedStatus: String?,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val automaticKey: String?,
    val ruleVersion: Int?,
    val ruleVerifiedEpochDay: Long?,
    val estimatedNotificationEpochDay: Long?,
    val lastNotifiedTrigger: String?,
    val referenceDateEpochDay: Long?,
    val completedByExpenseRecordPublicId: String?,
)

@Serializable
data class BackupAttachment(
    val publicId: String,
    val serviceRecordPublicId: String,
    val sequenceInRecord: Int,
    val displayName: String,
    val mimeType: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivePath: String,
)

object BackupDataBuilder {
    fun build(source: BackupDataSource): BackupSnapshot {
        val vehicleIds = source.vehicles.associate { it.id to it.publicId }
        val serviceTypeIds = source.serviceTypes.associate { it.id to it.publicId }
        val serviceRecordIds = source.serviceRecords.associate { it.id to it.publicId }
        val serviceItemIds = source.serviceItems.associate { it.id to it.publicId }
        val expenseIds = source.expenseRecords.associate { it.id to it.publicId }
        val reminderIds = source.reminders.associate { it.id to it.publicId }
        fun vehiclePublicId(id: Long) = requireNotNull(vehicleIds[id]) { "Backup references a missing vehicle." }
        fun serviceRecordPublicId(id: Long) = requireNotNull(serviceRecordIds[id]) { "Backup references a missing service record." }

        return BackupSnapshot(
            data = BackupData(
                vehicles = source.vehicles.map { entity -> entity.toBackup() },
                fuelRecords = source.fuelRecords.map { entity -> entity.toBackup(vehiclePublicId(entity.vehicleId)) },
                serviceTypes = source.serviceTypes.map { entity -> entity.toBackup() },
                serviceRecords = source.serviceRecords.map { entity -> entity.toBackup(vehiclePublicId(entity.vehicleId)) },
                serviceItems = source.serviceItems.map { entity ->
                    entity.toBackup(
                        serviceRecordPublicId(entity.serviceRecordId),
                        entity.serviceTypeId?.let { id -> requireNotNull(serviceTypeIds[id]) { "Backup references a missing service type." } },
                    )
                },
                expenseRecords = source.expenseRecords.map { entity ->
                    entity.toBackup(vehiclePublicId(entity.vehicleId), entity.completedReminderId?.let(reminderIds::get))
                },
                reminders = source.reminders.map { entity ->
                    entity.toBackup(
                        vehiclePublicId(entity.vehicleId),
                        entity.sourceServiceItemId?.let(serviceItemIds::get),
                        entity.completedByServiceRecordId?.let(serviceRecordIds::get),
                        entity.completedByExpenseRecordId?.let(expenseIds::get),
                    )
                },
                attachments = source.attachments.map { entity ->
                    entity.toBackup(serviceRecordPublicId(entity.serviceRecordId), attachmentArchivePath(entity.publicId))
                },
            ),
            attachmentSources = source.attachments.map { entity ->
                BackupAttachmentSource(entity.relativePath, attachmentArchivePath(entity.publicId))
            },
        )
    }

    private fun VehicleEntity.toBackup() = BackupVehicle(publicId, name, vehicleType.name, motorcycleClass?.name, brand, model, manufactureYear, engineDisplacementCc, licensePlate, powertrainType?.name, trackingStartDateEpochDay, trackingStartOdometerKm, currentOdometerKm, note, isArchived, createdAt, updatedAt, primaryInspectionMonthDay?.toString(), secondaryInspectionMonthDay?.toString())
    private fun FuelRecordEntity.toBackup(vehiclePublicId: String) = BackupFuelRecord(publicId, vehiclePublicId, dateEpochDay, timeMinuteOfDay, sequenceInDay, odometerKm, fuelVolumeMl, pricePerLiterMilli, totalCostTwd, isFullTank, fuelProduct?.name, note, createdAt, updatedAt, fuelingMode.name)
    private fun ServiceTypeEntity.toBackup() = BackupServiceType(publicId, name, vehicleType.name, isBuiltIn, isArchived, sortOrder, createdAt, updatedAt)
    private fun ServiceRecordEntity.toBackup(vehiclePublicId: String) = BackupServiceRecord(publicId, vehiclePublicId, dateEpochDay, timeMinuteOfDay, sequenceInDay, odometerKm, recordType.name, title, paymentMethod?.name, totalCostTwd, note, createdAt, updatedAt)
    private fun ServiceItemEntity.toBackup(serviceRecordPublicId: String, serviceTypePublicId: String?) = BackupServiceItem(publicId, serviceRecordPublicId, serviceTypePublicId, sequenceInRecord, nameSnapshot, quantityMilli, quantityUnit.name, unitPriceTwd, subtotalTwd, nextDueOdometerKm, nextDueDateEpochDay, note, createdAt, updatedAt)
    private fun ExpenseRecordEntity.toBackup(vehiclePublicId: String, completedReminderPublicId: String?) = BackupExpenseRecord(publicId, vehiclePublicId, dateEpochDay, timeMinuteOfDay, sequenceInDay, category.name, totalCostTwd, note, createdAt, updatedAt, completedReminderPublicId)
    private fun VehicleReminderEntity.toBackup(vehiclePublicId: String, sourceServiceItemPublicId: String?, completedByServiceRecordPublicId: String?, completedByExpenseRecordPublicId: String?) = BackupReminder(publicId, vehiclePublicId, source.name, sourceServiceItemPublicId, title, dueOdometerKm, dueDateEpochDay, completedByServiceRecordPublicId, completedAt, snoozedUntilEpochDay, lastNotifiedStatus?.name, isEnabled, createdAt, updatedAt, automaticKey, ruleVersion, ruleVerifiedEpochDay, estimatedNotificationEpochDay, lastNotifiedTrigger, referenceDateEpochDay, completedByExpenseRecordPublicId)
    private fun ServiceAttachmentEntity.toBackup(serviceRecordPublicId: String, archivePath: String) = BackupAttachment(publicId, serviceRecordPublicId, sequenceInRecord, displayName, mimeType, createdAt, updatedAt, archivePath)
    private fun attachmentArchivePath(publicId: String) = "attachments/$publicId"
}
