package com.kumo.bubu.data.backup

import com.kumo.bubu.data.backup.BackupArchiveTestData.completeData
import com.kumo.bubu.data.backup.BackupArchiveTestData.writeArchive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveSemanticValidationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun rejectsNegativeServiceTotalEvenWhenTheManifestAndChecksumAreCorrect() {
        val valid = BackupArchiveTestData.data()
        val invalid = valid.copy(serviceRecords = listOf(valid.serviceRecords.single().copy(totalCostTwd = -1)))
        val archive = temporary.newFile("negative-service-total.bubu")
        BackupArchiveWriter.write(
            destination = archive,
            appVersion = "1.1.1",
            createdAtEpochMillis = 1,
            recordCounts = BackupArchiveTestData.counts(),
            contents = listOf(BackupFileContent("data.json", Json.encodeToString(BackupData.serializer(), invalid).encodeToByteArray())),
        )

        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
    }
    @Test fun rejectsUnrepresentableServiceDatesAndInvalidLocalTimes() {
        val valid = BackupArchiveTestData.data()
        val record = valid.serviceRecords.single()
        val invalidRecords = listOf(
            record.copy(dateEpochDay = Long.MAX_VALUE),
            record.copy(dateEpochDay = Long.MIN_VALUE),
            record.copy(timeMinuteOfDay = -1),
            record.copy(timeMinuteOfDay = 1440),
        )
        invalidRecords.forEachIndexed { index, invalid ->
            val archive = temporary.writeArchive(valid.copy(serviceRecords = listOf(invalid)), "invalid-date-time-$index")
            assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun rejectsAReminderWhoseSourceItemBelongsToAnotherVehicle() {
        val valid = BackupArchiveTestData.data()
        val data = valid.copy(
            vehicles = valid.vehicles + valid.vehicles.single().copy(publicId = "vehicle-b"),
            serviceItems = listOf(BackupServiceItem(
                publicId = "item", serviceRecordPublicId = "service", serviceTypePublicId = null,
                sequenceInRecord = 0, nameSnapshot = "免費檢查", quantityMilli = 1000, quantityUnit = "PIECE",
                unitPriceTwd = 0, subtotalTwd = 0, nextDueOdometerKm = null, nextDueDateEpochDay = null,
                note = null, createdAt = 1, updatedAt = 1,
            )),
            reminders = listOf(BackupReminder(
                publicId = "reminder", vehiclePublicId = "vehicle-b", source = "SERVICE_ITEM",
                sourceServiceItemPublicId = "item", title = "提醒", dueOdometerKm = 1000, dueDateEpochDay = null,
                completedByServiceRecordPublicId = null, completedAt = null, snoozedUntilEpochDay = null,
                lastNotifiedStatus = null, isEnabled = true, createdAt = 1, updatedAt = 1,
                automaticKey = null, ruleVersion = null, ruleVerifiedEpochDay = null,
                estimatedNotificationEpochDay = null, lastNotifiedTrigger = null, referenceDateEpochDay = null,
                completedByExpenseRecordPublicId = null,
            )),
        )
        val archive = temporary.writeArchive(data, "cross-vehicle-reminder")

        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
    }

    @Test fun rejectsReminderCompletionByAnotherVehiclesServiceOrExpense() {
        val valid = BackupArchiveTestData.data()
        val reminder = BackupReminder(
            publicId = "reminder", vehiclePublicId = "vehicle", source = "MANUAL",
            sourceServiceItemPublicId = null, title = "提醒", dueOdometerKm = 1000, dueDateEpochDay = null,
            completedByServiceRecordPublicId = null, completedAt = 1, snoozedUntilEpochDay = null,
            lastNotifiedStatus = null, isEnabled = true, createdAt = 1, updatedAt = 1,
            automaticKey = null, ruleVersion = null, ruleVerifiedEpochDay = null,
            estimatedNotificationEpochDay = null, lastNotifiedTrigger = null, referenceDateEpochDay = null,
            completedByExpenseRecordPublicId = null,
        )
        val base = valid.copy(
            vehicles = valid.vehicles + valid.vehicles.single().copy(publicId = "vehicle-b"),
            serviceRecords = valid.serviceRecords + valid.serviceRecords.single().copy(publicId = "other-service", vehiclePublicId = "vehicle-b"),
            serviceItems = valid.serviceItems + valid.serviceItems.single().copy(publicId = "other-item", serviceRecordPublicId = "other-service"),
            expenseRecords = listOf(BackupExpenseRecord(
                publicId = "other-expense", vehiclePublicId = "vehicle-b", dateEpochDay = 20_000,
                timeMinuteOfDay = null, sequenceInDay = 0, category = "INSURANCE", totalCostTwd = 0,
                note = null, createdAt = 1, updatedAt = 1, completedReminderPublicId = null,
            )),
        )
        listOf(
            reminder.copy(completedByServiceRecordPublicId = "other-service"),
            reminder.copy(completedByExpenseRecordPublicId = "other-expense"),
        ).forEachIndexed { index, invalid ->
            val archive = temporary.writeArchive(base.copy(reminders = listOf(invalid)), "cross-vehicle-completion-$index")
            assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun rejectsFuelAmountsAndDatesOutsideTheirValidRanges() {
        val base = BackupArchiveTestData.data()
        val fuel = BackupFuelRecord(
            publicId = "fuel", vehiclePublicId = "vehicle", dateEpochDay = 20_000, timeMinuteOfDay = null,
            sequenceInDay = 0, odometerKm = 1, fuelVolumeMl = 1000, pricePerLiterMilli = null,
            totalCostTwd = 0, isFullTank = false, fuelProduct = null, note = null, createdAt = 1, updatedAt = 1,
        )
        listOf(
            fuel.copy(totalCostTwd = -1), fuel.copy(fuelVolumeMl = 0), fuel.copy(fuelVolumeMl = 1_000_000),
            fuel.copy(pricePerLiterMilli = -1), fuel.copy(odometerKm = -1),
            fuel.copy(dateEpochDay = Long.MAX_VALUE), fuel.copy(timeMinuteOfDay = -1), fuel.copy(timeMinuteOfDay = 1440),
        ).forEachIndexed { index, invalid ->
            val archive = temporary.writeArchive(base.copy(fuelRecords = listOf(invalid)), "invalid-fuel-$index")
            assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun rejectsInvalidExpenseAmountsAndLocalDates() {
        val valid = BackupArchiveTestData.data()
        val expense = BackupExpenseRecord(
            publicId = "expense", vehiclePublicId = "vehicle", dateEpochDay = 20_000,
            timeMinuteOfDay = null, sequenceInDay = 0, category = "INSURANCE", totalCostTwd = 0,
            note = null, createdAt = 1, updatedAt = 1, completedReminderPublicId = null,
        )
        listOf(
            expense.copy(totalCostTwd = -1), expense.copy(dateEpochDay = Long.MIN_VALUE),
            expense.copy(timeMinuteOfDay = -1), expense.copy(timeMinuteOfDay = 1440),
        ).forEachIndexed { index, invalid ->
            val archive = temporary.writeArchive(valid.copy(expenseRecords = listOf(invalid)), "invalid-expense-$index")
            assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun rejectsAServiceRecordWithoutAnyItems() {
        val archive = temporary.writeArchive(BackupArchiveTestData.data().copy(serviceItems = emptyList()), "empty-work-order")
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
    }

    @Test fun rejectsInvalidItemSubtotalsWorkOrderTotalsAndArithmeticOverflow() {
        val base = BackupArchiveTestData.data()
        val item = base.serviceItems.single()
        val record = base.serviceRecords.single()
        val invalid = listOf(
            base.copy(serviceItems = listOf(item.copy(quantityMilli = 0))),
            base.copy(serviceItems = listOf(item.copy(quantityMilli = -1))),
            base.copy(serviceItems = listOf(item.copy(unitPriceTwd = -1))),
            base.copy(serviceItems = listOf(item.copy(subtotalTwd = -1))),
            base.copy(serviceItems = listOf(item.copy(subtotalTwd = 1))),
            base.copy(serviceRecords = listOf(record.copy(odometerKm = -1))),
            base.copy(serviceRecords = listOf(record.copy(totalCostTwd = 1))),
            base.copy(serviceItems = listOf(item.copy(quantityMilli = Long.MAX_VALUE, unitPriceTwd = Long.MAX_VALUE, subtotalTwd = Long.MAX_VALUE)), serviceRecords = listOf(record.copy(totalCostTwd = Long.MAX_VALUE))),
            base.copy(serviceItems = listOf(item.copy(quantityMilli = 1500, unitPriceTwd = 1, subtotalTwd = 1)), serviceRecords = listOf(record.copy(totalCostTwd = 1))),
            base.copy(serviceItems = listOf(
                item.copy(unitPriceTwd = Long.MAX_VALUE, subtotalTwd = Long.MAX_VALUE),
                item.copy(publicId = "second-item", unitPriceTwd = Long.MAX_VALUE, subtotalTwd = Long.MAX_VALUE),
            ), serviceRecords = listOf(record.copy(totalCostTwd = Long.MAX_VALUE))),
        )
        invalid.forEachIndexed { index, data ->
            val archive = temporary.writeArchive(data, "invalid-service-money-$index")
            assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun rejectsUnknownExplicitEnumsBeforeAnyDatabaseReplacement() {
        val data = completeData()
        val invalid = listOf(
            data.copy(vehicles = listOf(data.vehicles.single().copy(vehicleType = "UNKNOWN"))),
            data.copy(vehicles = listOf(data.vehicles.single().copy(motorcycleClass = "UNKNOWN"))),
            data.copy(vehicles = listOf(data.vehicles.single().copy(powertrainType = "UNKNOWN"))),
            data.copy(fuelRecords = listOf(data.fuelRecords.single().copy(fuelProduct = "UNKNOWN"))),
            data.copy(fuelRecords = listOf(data.fuelRecords.single().copy(fuelingMode = "UNKNOWN"))),
            data.copy(fuelRecords = listOf(data.fuelRecords.single().copy(fuelEconomyStatisticsStatus = "UNKNOWN"))),
            data.copy(serviceTypes = listOf(data.serviceTypes.single().copy(vehicleType = "UNKNOWN"))),
            data.copy(serviceRecords = listOf(data.serviceRecords.single().copy(recordType = "UNKNOWN"))),
            data.copy(serviceRecords = listOf(data.serviceRecords.single().copy(paymentMethod = "UNKNOWN"))),
            data.copy(serviceItems = listOf(data.serviceItems.single().copy(quantityUnit = "UNKNOWN"))),
            data.copy(expenseRecords = listOf(data.expenseRecords.single().copy(category = "UNKNOWN"))),
            data.copy(reminders = listOf(data.reminders.single().copy(source = "UNKNOWN"))),
            data.copy(reminders = listOf(data.reminders.single().copy(lastNotifiedStatus = "UNKNOWN"))),
            data.copy(serviceReminderPreferences = listOf(data.serviceReminderPreferences.single().copy(reminderType = "UNKNOWN"))),
        )
        invalid.forEachIndexed { index, invalidData ->
            val archive = temporary.writeArchive(invalidData, "unknown-enum-$index")
            assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun serviceTypeNamesAreUniqueWithinEachVehicleType() {
        val data = completeData()
        val type = data.serviceTypes.single()
        val duplicate = type.copy(publicId = "second-type")
        val invalid = temporary.writeArchive(data.copy(serviceTypes = listOf(type, duplicate)), "duplicate-type-name")
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(invalid) }

        val valid = temporary.writeArchive(data.copy(serviceTypes = listOf(type, duplicate.copy(vehicleType = "MOTORCYCLE"))), "same-name-different-vehicle-type")
        assertEquals(2, BackupArchiveReader.read(valid).data.serviceTypes.size)
    }

    @Test fun reminderSourcesAndAutomaticKeysAreUniqueWhenPresent() {
        val data = completeData()
        val reminder = data.reminders.single()
        val manual = reminder.copy(source = "MANUAL", sourceServiceItemPublicId = null)
        val invalid = listOf(
            listOf(reminder, reminder.copy(publicId = "second-reminder")),
            listOf(manual.copy(automaticKey = "inspection"), manual.copy(publicId = "second-reminder", automaticKey = "inspection")),
        )
        invalid.forEachIndexed { index, reminders ->
            val archive = temporary.writeArchive(data.copy(reminders = reminders), "duplicate-reminder-key-$index")
            assertThrows("case $index", IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }

        val valid = temporary.writeArchive(data.copy(reminders = listOf(manual, manual.copy(publicId = "second-reminder"))), "multiple-null-reminder-keys")
        assertEquals(2, BackupArchiveReader.read(valid).data.reminders.size)
    }

    @Test fun rejectsDuplicatePoliciesForTheSameVehicleAndServiceType() {
        val data = completeData()
        val policy = data.serviceReminderPreferences.single()
        val archive = temporary.writeArchive(data.copy(serviceReminderPreferences = listOf(policy, policy.copy(publicId = "second-policy", sortOrder = 1))), "duplicate-policy")
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
    }

    @Test fun rejectsAnExpenseThatCompletesAnotherVehiclesReminder() {
        val valid = completeData()
        val invalid = valid.copy(
            vehicles = valid.vehicles + valid.vehicles.single().copy(publicId = "vehicle-b"),
            expenseRecords = listOf(valid.expenseRecords.single().copy(vehiclePublicId = "vehicle-b", completedReminderPublicId = "reminder")),
        )
        val archive = temporary.writeArchive(invalid, "cross-vehicle-expense-reminder")
        assertThrows(IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
    }

    @Test fun rejectsInvalidVehicleAndSchedulingRanges() {
        val base = completeData()
        val vehicle = base.vehicles.single()
        val item = base.serviceItems.single()
        val reminder = base.reminders.single()
        val policy = base.serviceReminderPreferences.single()
        val invalid = listOf(
            base.copy(vehicles = listOf(vehicle.copy(trackingStartOdometerKm = -1))),
            base.copy(vehicles = listOf(vehicle.copy(currentOdometerKm = -1))),
            base.copy(vehicles = listOf(vehicle.copy(engineDisplacementCc = 0))),
            base.copy(vehicles = listOf(vehicle.copy(manufactureYear = 1885))),
            base.copy(vehicles = listOf(vehicle.copy(manufactureYear = Int.MAX_VALUE))),
            base.copy(vehicles = listOf(vehicle.copy(trackingStartDateEpochDay = Long.MAX_VALUE))),
            base.copy(vehicles = listOf(vehicle.copy(primaryInspectionMonthDay = "--02-30"))),
            base.copy(vehicles = listOf(vehicle.copy(secondaryInspectionMonthDay = "--13-01"))),
            base.copy(vehicles = listOf(vehicle.copy(vehicleType = "MOTORCYCLE", motorcycleClass = null)), serviceTypes = listOf(base.serviceTypes.single().copy(vehicleType = "MOTORCYCLE"))),
            base.copy(serviceItems = listOf(item.copy(nextDueOdometerKm = -1))),
            base.copy(serviceItems = listOf(item.copy(nextDueDateEpochDay = Long.MAX_VALUE))),
            base.copy(reminders = listOf(reminder.copy(dueOdometerKm = -1))),
            base.copy(reminders = listOf(reminder.copy(dueOdometerKm = null, dueDateEpochDay = null))),
            base.copy(reminders = listOf(reminder.copy(dueDateEpochDay = Long.MAX_VALUE))),
            base.copy(reminders = listOf(reminder.copy(snoozedUntilEpochDay = Long.MAX_VALUE))),
            base.copy(reminders = listOf(reminder.copy(ruleVerifiedEpochDay = Long.MAX_VALUE))),
            base.copy(reminders = listOf(reminder.copy(estimatedNotificationEpochDay = Long.MAX_VALUE))),
            base.copy(reminders = listOf(reminder.copy(referenceDateEpochDay = Long.MAX_VALUE))),
            base.copy(serviceReminderPreferences = listOf(policy.copy(intervalKm = 0))),
            base.copy(serviceReminderPreferences = listOf(policy.copy(intervalMonths = 0))),
            base.copy(serviceReminderPreferences = listOf(policy.copy(baseOdometerKm = -1))),
            base.copy(serviceReminderPreferences = listOf(policy.copy(sortOrder = -1))),
        )
        invalid.forEachIndexed { index, data ->
            val archive = temporary.writeArchive(data, "invalid-vehicle-scheduling-$index")
            assertThrows("case $index", IllegalArgumentException::class.java) { BackupArchiveReader.read(archive) }
        }
    }

    @Test fun acceptsLegitimateZeroCostsUnknownTimesAndRoundedServiceItems() {
        val data = completeData().copy(
            serviceItems = listOf(completeData().serviceItems.single().copy(quantityMilli = 1500, unitPriceTwd = 1, subtotalTwd = 2)),
            serviceRecords = listOf(completeData().serviceRecords.single().copy(totalCostTwd = 2)),
        )
        val archive = temporary.writeArchive(data, "valid-decimal-service")
        val restored = BackupArchiveReader.read(archive).data
        assertEquals(2L, restored.serviceItems.single().subtotalTwd)
        assertEquals(2L, restored.serviceRecords.single().totalCostTwd)
        assertEquals(0L, restored.fuelRecords.single().totalCostTwd)
        assertEquals(0L, restored.expenseRecords.single().totalCostTwd)
        assertEquals(null, restored.fuelRecords.single().pricePerLiterMilli)
        assertEquals(null, restored.fuelRecords.single().timeMinuteOfDay)
    }

}
