package com.kumo.bubu.data.backup

import com.kumo.bubu.data.backup.BackupArchiveLimits.MAX_DATA_BYTES
import com.kumo.bubu.data.backup.BackupArchiveLimits.MAX_MANIFEST_BYTES
import com.kumo.bubu.domain.model.MAX_FUEL_VOLUME_ML
import com.kumo.bubu.domain.model.calculateServiceSubtotalTwd
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json

data class BackupPreview(
    val manifest: BackupManifest,
    val data: BackupData,
    val totalByteCount: Long,
)

object BackupArchiveReader {
    fun read(archiveFile: File): BackupPreview {
        BackupArchiveLimits.requireSize(archiveFile.length(), BackupArchiveLimits.MAX_ARCHIVE_BYTES)
        ZipFile(archiveFile).use { archive ->
            val entries = archive.entries().asSequence().toList()
            require(entries.none { it.isDirectory }) { "Backup cannot contain directory entries." }
            require(entries.map { it.name }.all(::isSafeRelativePath)) { "Backup contains an unsafe path." }
            require(entries.map { it.name }.distinct().size == entries.size) { "Backup contains duplicate paths." }
            val manifestEntry = entries.singleOrNull { it.name == MANIFEST_ENTRY }
                ?: throw IllegalArgumentException("Backup manifest is missing.")
            val manifest = archive.getInputStream(manifestEntry).use { input ->
                JSON.decodeFromString(
                    BackupManifest.serializer(),
                    input.readAtMost(MAX_MANIFEST_BYTES).decodeToString(),
                )
            }
            require(manifest.formatVersion in 1..BACKUP_FORMAT_VERSION) { "Backup format is unsupported." }
            val declaredFiles = manifest.files.associateBy(BackupManifestFile::relativePath)
            require(declaredFiles.size == manifest.files.size) { "Backup manifest contains duplicate paths." }
            require(declaredFiles.keys.all(::isSafeRelativePath)) { "Backup manifest contains an unsafe path." }
            require(manifest.files.all { it.sizeBytes >= 0L }) { "Backup manifest contains an invalid file size." }
            manifest.files.totalSizeBytes()
            require(entries.map { it.name }.toSet() == declaredFiles.keys + MANIFEST_ENTRY) {
                "Backup contents do not match its manifest."
            }
            declaredFiles.forEach { (path, metadata) ->
                BackupArchiveLimits.requireSize(metadata.sizeBytes, BackupArchiveLimits.maxBytesFor(path).toLong())
                val entry = entries.single { it.name == path }
                val bytes = archive.getInputStream(entry).use { input ->
                    input.readAtMost(BackupArchiveLimits.maxBytesFor(path))
                }
                require(bytes.size.toLong() == metadata.sizeBytes) { "Backup file size does not match." }
                require(bytes.sha256() == metadata.sha256) { "Backup file hash does not match." }
            }
            val dataBytes = archive.getInputStream(entries.single { it.name == DATA_ENTRY }).use { input ->
                input.readAtMost(MAX_DATA_BYTES)
            }
            val data = JSON.decodeFromString(BackupData.serializer(), dataBytes.decodeToString())
            validateData(manifest, data)
            return BackupPreview(
                manifest = manifest,
                data = data,
                totalByteCount = manifest.files.sumOf(BackupManifestFile::sizeBytes),
            )
        }
    }

    private fun validateData(manifest: BackupManifest, data: BackupData) {
        require(manifest.recordCounts == recordCounts(data)) { "Backup record counts do not match." }
        require(manifest.attachmentCount == data.attachments.size) { "Backup attachment count does not match." }
        require(data.vehicles.all { it.vehicleType.isKnownEnum<VehicleType>() && it.motorcycleClass.isKnownEnum<MotorcycleClass>() && it.powertrainType.isKnownEnum<PowertrainType>() }) { "Backup contains an unknown vehicle enum." }
        require(data.fuelRecords.all { it.fuelProduct.isKnownEnum<FuelProduct>() && it.fuelingMode.isKnownEnum<FuelingMode>() && it.fuelEconomyStatisticsStatus.isKnownEnum<FuelEconomyStatisticsStatus>() }) { "Backup contains an unknown fuel enum." }
        require(data.serviceTypes.all { it.vehicleType.isKnownEnum<VehicleType>() }) { "Backup contains an unknown service-type enum." }
        require(data.serviceRecords.all { it.recordType.isKnownEnum<ServiceRecordType>() && it.paymentMethod.isKnownEnum<PaymentMethod>() }) { "Backup contains an unknown service enum." }
        require(data.serviceItems.all { it.quantityUnit.isKnownEnum<ServiceQuantityUnit>() }) { "Backup contains an unknown quantity unit." }
        require(data.expenseRecords.all { it.category.isKnownEnum<ExpenseCategory>() }) { "Backup contains an unknown expense category." }
        require(data.reminders.all { it.source.isKnownEnum<ReminderSource>() && it.lastNotifiedStatus.isKnownEnum<ReminderStatus>() }) { "Backup contains an unknown reminder enum." }
        require(data.vehicles.all { vehicle ->
            vehicle.trackingStartOdometerKm >= 0L && vehicle.currentOdometerKm >= 0L &&
                vehicle.trackingStartDateEpochDay.isValidEpochDay() &&
                (vehicle.manufactureYear == null || vehicle.manufactureYear in 1886..Year.MAX_VALUE) &&
                (vehicle.engineDisplacementCc == null || vehicle.engineDisplacementCc > 0) &&
                (vehicle.vehicleType != VehicleType.MOTORCYCLE.name || vehicle.motorcycleClass != null) &&
                vehicle.primaryInspectionMonthDay.isValidMonthDay() && vehicle.secondaryInspectionMonthDay.isValidMonthDay()
        }) { "Backup contains invalid vehicle data." }
        require(data.serviceItems.all { item ->
            (item.nextDueOdometerKm == null || item.nextDueOdometerKm >= 0L) && item.nextDueDateEpochDay.isValidOptionalEpochDay()
        }) { "Backup contains an invalid service-item due value." }
        require(data.reminders.all { reminder ->
            (reminder.dueOdometerKm != null || reminder.dueDateEpochDay != null) &&
                (reminder.dueOdometerKm == null || reminder.dueOdometerKm >= 0L) &&
                reminder.dueDateEpochDay.isValidOptionalEpochDay() && reminder.snoozedUntilEpochDay.isValidOptionalEpochDay() &&
                reminder.ruleVerifiedEpochDay.isValidOptionalEpochDay() && reminder.estimatedNotificationEpochDay.isValidOptionalEpochDay() &&
                reminder.referenceDateEpochDay.isValidOptionalEpochDay()
        }) { "Backup contains invalid reminder dates or due values." }
        require(data.serviceReminderPreferences.all { policy ->
            (policy.intervalKm == null || policy.intervalKm > 0L) && (policy.intervalMonths == null || policy.intervalMonths > 0L) &&
                (policy.baseOdometerKm == null || policy.baseOdometerKm >= 0L) && policy.sortOrder >= 0
        }) { "Backup contains invalid maintenance intervals." }
        require(data.fuelRecords.all { fuel ->
            fuel.odometerKm >= 0L && fuel.totalCostTwd >= 0L &&
                fuel.fuelVolumeMl in 1..MAX_FUEL_VOLUME_ML &&
                (fuel.pricePerLiterMilli == null || fuel.pricePerLiterMilli >= 0L) &&
                fuel.dateEpochDay.isValidEpochDay() && (fuel.timeMinuteOfDay == null || fuel.timeMinuteOfDay in 0..1439)
        }) { "Backup contains invalid fuel data." }
        require(data.expenseRecords.all { expense ->
            expense.totalCostTwd >= 0L && expense.dateEpochDay.isValidEpochDay() &&
                (expense.timeMinuteOfDay == null || expense.timeMinuteOfDay in 0..1439)
        }) { "Backup contains invalid expense data." }
        require(data.serviceRecords.all { it.totalCostTwd >= 0L && it.odometerKm >= 0L }) { "Backup contains an invalid service amount or odometer." }
        require(data.serviceItems.all { item ->
            calculateServiceSubtotalTwd(item.quantityMilli, item.unitPriceTwd) == item.subtotalTwd
        }) { "Backup contains an invalid service item subtotal." }
        val serviceTotals = data.serviceItems.groupBy(BackupServiceItem::serviceRecordPublicId).mapValues { (_, items) ->
            try {
                items.fold(0L) { total, item -> Math.addExact(total, item.subtotalTwd) }
            } catch (_: ArithmeticException) {
                null
            }
        }
        require(data.serviceRecords.all { it.publicId in serviceTotals }) { "Backup contains a service record without items." }
        require(data.serviceRecords.all { serviceTotals[it.publicId] == it.totalCostTwd }) { "Backup service total does not match its items." }
        require(data.serviceRecords.all { record ->
            record.dateEpochDay.isValidEpochDay() && (record.timeMinuteOfDay == null || record.timeMinuteOfDay in 0..1439)
        }) { "Backup contains an invalid service date or time." }
        require(data.vehicles.uniquePublicIds(BackupVehicle::publicId)) { "Backup contains duplicate vehicle identifiers." }
        require(data.fuelRecords.uniquePublicIds(BackupFuelRecord::publicId)) { "Backup contains duplicate fuel identifiers." }
        require(data.serviceTypes.uniquePublicIds(BackupServiceType::publicId)) { "Backup contains duplicate service-type identifiers." }
        require(data.serviceTypes.map { it.vehicleType to it.name }.distinct().size == data.serviceTypes.size) { "Backup contains duplicate service-type names for a vehicle type." }
        require(data.serviceRecords.uniquePublicIds(BackupServiceRecord::publicId)) { "Backup contains duplicate service identifiers." }
        require(data.serviceItems.uniquePublicIds(BackupServiceItem::publicId)) { "Backup contains duplicate service-item identifiers." }
        require(data.expenseRecords.uniquePublicIds(BackupExpenseRecord::publicId)) { "Backup contains duplicate expense identifiers." }
        require(data.reminders.uniquePublicIds(BackupReminder::publicId)) { "Backup contains duplicate reminder identifiers." }
        require(data.reminders.mapNotNull(BackupReminder::sourceServiceItemPublicId).let { it.distinct().size == it.size }) { "Backup contains duplicate reminder source items." }
        require(data.reminders.mapNotNull(BackupReminder::automaticKey).let { it.distinct().size == it.size }) { "Backup contains duplicate automatic reminder keys." }
        require(data.serviceReminderPreferences.uniquePublicIds(BackupServiceReminderPreference::publicId)) { "Backup contains duplicate service reminder preference identifiers." }
        require(data.serviceReminderPreferences.map { it.vehiclePublicId to it.serviceTypePublicId }.distinct().size == data.serviceReminderPreferences.size) { "Backup contains duplicate vehicle/service-type policies." }
        require(data.attachments.uniquePublicIds(BackupAttachment::publicId)) { "Backup contains duplicate attachment identifiers." }
        val vehicleIds = data.vehicles.mapTo(mutableSetOf(), BackupVehicle::publicId)
        val typeIds = data.serviceTypes.mapTo(mutableSetOf(), BackupServiceType::publicId)
        val serviceIds = data.serviceRecords.mapTo(mutableSetOf(), BackupServiceRecord::publicId)
        val reminderVehicleIds = data.reminders.associate { it.publicId to it.vehiclePublicId }
        require(data.fuelRecords.all { it.vehiclePublicId in vehicleIds })
        require(data.serviceRecords.all { it.vehiclePublicId in vehicleIds })
        require(data.expenseRecords.all { it.vehiclePublicId in vehicleIds && (it.completedReminderPublicId == null || reminderVehicleIds[it.completedReminderPublicId] == it.vehiclePublicId) }) { "Backup expense references another vehicle's reminder." }
        val serviceVehicleIds = data.serviceRecords.associate { it.publicId to it.vehiclePublicId }
        // Migration 8 -> 9 classified formerly shared types as CAR without rewriting history.
        require(data.serviceItems.all { item ->
            item.serviceRecordPublicId in serviceIds &&
                (item.serviceTypePublicId == null || item.serviceTypePublicId in typeIds)
        }) { "Backup service item references a missing work order or service type." }
        val itemVehicleIds = data.serviceItems.associate { it.publicId to serviceVehicleIds.getValue(it.serviceRecordPublicId) }
        val expenseVehicleIds = data.expenseRecords.associate { it.publicId to it.vehiclePublicId }
        require(data.reminders.all { reminder ->
            reminder.vehiclePublicId in vehicleIds &&
                (reminder.sourceServiceItemPublicId == null || itemVehicleIds[reminder.sourceServiceItemPublicId] == reminder.vehiclePublicId) &&
                (reminder.completedByServiceRecordPublicId == null || serviceVehicleIds[reminder.completedByServiceRecordPublicId] == reminder.vehiclePublicId) &&
                (reminder.completedByExpenseRecordPublicId == null || expenseVehicleIds[reminder.completedByExpenseRecordPublicId] == reminder.vehiclePublicId)
        })
        require(data.serviceReminderPreferences.all { preference ->
            preference.vehiclePublicId in vehicleIds &&
                preference.serviceTypePublicId in typeIds &&
                preference.reminderType in com.kumo.bubu.domain.model.MaintenanceReminderType.entries.map { it.name }
        })
        require(data.attachments.all { it.serviceRecordPublicId in serviceIds })
        require(data.attachments.map(BackupAttachment::archivePath).toSet() == manifest.files
            .map(BackupManifestFile::relativePath)
            .filter { it.startsWith(ATTACHMENTS_DIRECTORY) }
            .toSet()) { "Backup attachment list does not match its files." }
    }

    private fun recordCounts(data: BackupData): Map<String, Int> = mapOf(
        "vehicles" to data.vehicles.size,
        "fuelRecords" to data.fuelRecords.size,
        "serviceTypes" to data.serviceTypes.size,
        "serviceRecords" to data.serviceRecords.size,
        "serviceItems" to data.serviceItems.size,
        "expenseRecords" to data.expenseRecords.size,
        "reminders" to data.reminders.size,
        "attachments" to data.attachments.size,
    )

    private inline fun <reified E : Enum<E>> String?.isKnownEnum(): Boolean {
        val name = this ?: return true
        return try {
            enumValueOf<E>(name)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun Long?.isValidOptionalEpochDay(): Boolean = this?.isValidEpochDay() ?: true

    private fun String?.isValidMonthDay(): Boolean = this == null || runCatching { MonthDay.parse(this) }.isSuccess

    private fun Long.isValidEpochDay(): Boolean = this in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()

    private fun <T> List<T>.uniquePublicIds(publicId: (T) -> String): Boolean =
        map(publicId).distinct().size == size

    private fun List<BackupManifestFile>.totalSizeBytes(): Long = fold(0L) { total, file ->
        BackupArchiveLimits.addContentSize(total, file.sizeBytes)
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
            path.split('/').all { segment -> segment.isNotBlank() && segment != "." && segment != ".." }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            BackupArchiveLimits.requireSize(total.toLong(), maxBytes.toLong())
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DATA_ENTRY = "data.json"
    private const val ATTACHMENTS_DIRECTORY = "attachments/"
    private val JSON = Json { ignoreUnknownKeys = false }
}
