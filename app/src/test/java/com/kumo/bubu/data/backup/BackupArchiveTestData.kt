package com.kumo.bubu.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.rules.TemporaryFolder

internal object BackupArchiveTestData {
    fun data(attachmentCount: Int = 0) = BackupData(
        vehicles = listOf(BackupVehicle(
            publicId = "vehicle", name = "車", vehicleType = "CAR", motorcycleClass = null,
            brand = null, model = null, manufactureYear = null, engineDisplacementCc = null,
            licensePlate = null, powertrainType = null, trackingStartDateEpochDay = 20_000,
            trackingStartOdometerKm = 0, currentOdometerKm = 0, note = null, isArchived = false,
            createdAt = 1, updatedAt = 1, primaryInspectionMonthDay = null, secondaryInspectionMonthDay = null,
        )),
        fuelRecords = emptyList(), serviceTypes = emptyList(),
        serviceRecords = listOf(BackupServiceRecord(
            publicId = "service", vehiclePublicId = "vehicle", dateEpochDay = 20_000,
            timeMinuteOfDay = null, sequenceInDay = 0, odometerKm = 0, recordType = "MAINTENANCE",
            title = "工單", paymentMethod = null, totalCostTwd = 0, note = null, createdAt = 1, updatedAt = 1,
        )),
        serviceItems = listOf(BackupServiceItem(
            publicId = "fixture-item", serviceRecordPublicId = "service", serviceTypePublicId = null,
            sequenceInRecord = 0, nameSnapshot = "免費檢查", quantityMilli = 1000, quantityUnit = "PIECE",
            unitPriceTwd = 0, subtotalTwd = 0, nextDueOdometerKm = null, nextDueDateEpochDay = null,
            note = null, createdAt = 1, updatedAt = 1,
        )), expenseRecords = emptyList(), reminders = emptyList(),
        attachments = List(attachmentCount) { index -> BackupAttachment(
            publicId = "receipt-${index + 1}", serviceRecordPublicId = "service", sequenceInRecord = index,
            displayName = "收據.png", mimeType = "image/png", createdAt = 1, updatedAt = 1,
            archivePath = "attachments/receipt-${index + 1}",
        ) },
    )

    fun json(attachmentCount: Int = 0): ByteArray =
        Json.encodeToString(BackupData.serializer(), data(attachmentCount)).encodeToByteArray()

    fun counts(attachmentCount: Int = 0): Map<String, Int> = mapOf(
        "vehicles" to 1, "fuelRecords" to 0, "serviceTypes" to 0, "serviceRecords" to 1,
        "serviceItems" to 1, "expenseRecords" to 0, "reminders" to 0, "attachments" to attachmentCount,
    )

    private val encoderWithDefaults = Json { encodeDefaults = true }

    fun encodeWithDefaults(data: BackupData): JsonObject =
        encoderWithDefaults.encodeToJsonElement(BackupData.serializer(), data).jsonObject

    fun completeData(): BackupData {
        val base = BackupArchiveTestData.data()
        return base.copy(
            serviceTypes = listOf(BackupServiceType("type", "保養", "CAR", false, false, 0, 1, 1)),
            serviceItems = listOf(base.serviceItems.single().copy(serviceTypePublicId = "type")),
            fuelRecords = listOf(BackupFuelRecord("fuel", "vehicle", 20_000, null, 0, 0, 1000, null, 0, false, null, null, 1, 1)),
            expenseRecords = listOf(BackupExpenseRecord("expense", "vehicle", 20_000, null, 0, "INSURANCE", 0, null, 1, 1, null)),
            reminders = listOf(BackupReminder(
                publicId = "reminder", vehiclePublicId = "vehicle", source = "SERVICE_ITEM",
                sourceServiceItemPublicId = "fixture-item", title = "提醒", dueOdometerKm = 1000, dueDateEpochDay = null,
                completedByServiceRecordPublicId = null, completedAt = null, snoozedUntilEpochDay = null,
                lastNotifiedStatus = null, isEnabled = true, createdAt = 1, updatedAt = 1,
                automaticKey = null, ruleVersion = null, ruleVerifiedEpochDay = null,
                estimatedNotificationEpochDay = null, lastNotifiedTrigger = null, referenceDateEpochDay = null,
                completedByExpenseRecordPublicId = null,
            )),
            serviceReminderPreferences = listOf(BackupServiceReminderPreference("policy", "vehicle", "type", true, 1000, 3, "REPLACE", 0, 0, 1, 1)),
        )
    }

    fun TemporaryFolder.writeArchive(data: BackupData, label: String, encodedData: ByteArray = Json.encodeToString(BackupData.serializer(), data).encodeToByteArray()): java.io.File = newFile("$label.bubu").also { archive ->
        BackupArchiveWriter.write(
            destination = archive,
            appVersion = "1.1.1",
            createdAtEpochMillis = 1,
            recordCounts = mapOf(
                "vehicles" to data.vehicles.size, "fuelRecords" to data.fuelRecords.size,
                "serviceTypes" to data.serviceTypes.size, "serviceRecords" to data.serviceRecords.size,
                "serviceItems" to data.serviceItems.size, "expenseRecords" to data.expenseRecords.size,
                "reminders" to data.reminders.size, "attachments" to data.attachments.size,
            ),
            contents = listOf(BackupFileContent("data.json", encodedData)),
        )
    }

}
