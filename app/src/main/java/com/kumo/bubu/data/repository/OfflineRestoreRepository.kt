package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.room.withTransaction
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.backup.BackupArchiveReader
import com.kumo.bubu.data.backup.BackupArchiveVerifier
import com.kumo.bubu.data.backup.BackupArchiveLimits
import com.kumo.bubu.data.backup.BackupAttachment
import com.kumo.bubu.data.backup.BackupData
import com.kumo.bubu.data.backup.BackupPreview
import com.kumo.bubu.data.local.entity.ExpenseRecordEntity
import com.kumo.bubu.data.local.entity.PendingAttachmentDeletionEntity
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import com.kumo.bubu.data.local.entity.VehicleServiceReminderPreferenceEntity
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.RestorePreview
import com.kumo.bubu.domain.repository.RestoreRepository
import com.kumo.bubu.domain.repository.RestoreResult
import com.kumo.bubu.domain.repository.RecoveryBackup
import com.kumo.bubu.domain.model.StagedServiceAttachment
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class OfflineRestoreRepository(
    context: Context,
    private val database: BuBuDatabase,
    private val attachmentStore: PrivateAttachmentStore,
    private val backupRepository: OfflineBackupRepository,
    recoveryDirectory: File? = null,
    // Count free blocks now, rather than assuming reclaimable app caches are allocated.
    private val availablePrivateBytes: (File) -> Long = { StatFs(it.path).availableBytes },
) : RestoreRepository {
    private val applicationContext = context.applicationContext
    private val restoreCacheDirectory = File(applicationContext.cacheDir, RESTORE_CACHE_DIRECTORY)
    private val recoveryDirectory = recoveryDirectory ?: File(applicationContext.filesDir, RECOVERY_DIRECTORY)

    override suspend fun preview(sourceUriString: String): RestorePreview = withCandidateFile(sourceUriString) { candidate ->
        BackupArchiveReader.read(candidate).toDomain()
    }

    override suspend fun restore(sourceUriString: String): RestoreResult = withCandidateFile(sourceUriString) { candidate ->
        val preview = BackupArchiveReader.read(candidate)
        ensureSpaceForRestore(candidate, preview)
        val recovery = createRecoveryBackup()
        try {
            replaceCurrentData(candidate)
            RestoreResult(recoveryBackupFileName = recovery.name)
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                try {
                    replaceCurrentData(recovery)
                } catch (recoveryError: Throwable) {
                    throw RestoreException("Restore and automatic recovery both failed.", recoveryError)
                }
            }
            if (error is CancellationException) throw error
            throw RestoreException("Restore failed; the previous data was recovered.", error)
        }
    }

    override suspend fun getLatestRecoveryBackup(): RecoveryBackup? = withContext(Dispatchers.IO) {
        latestRecoveryFile()?.toRecoveryBackup()
    }

    override suspend fun exportLatestRecoveryBackup(destinationUriString: String): RecoveryBackup =
        withContext(Dispatchers.IO) {
            val recovery = requireNotNull(latestRecoveryFile()) { "No recovery backup is available." }
            val destination = Uri.parse(destinationUriString)
            try {
                FileInputStream(recovery).use { input ->
                    requireNotNull(applicationContext.contentResolver.openOutputStream(destination, "w")) {
                        "Selected recovery-backup destination cannot be opened."
                    }.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                recovery.toRecoveryBackup()
            } catch (error: Throwable) {
                runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, destination) }
                throw RestoreException("Recovery backup could not be exported.", error)
            }
        }

    override suspend fun deleteLatestRecoveryBackup(): Boolean = withContext(Dispatchers.IO) {
        latestRecoveryFile()?.delete() ?: false
    }

    private suspend fun <T> withCandidateFile(
        sourceUriString: String,
        action: suspend (File) -> T,
    ): T = withContext(Dispatchers.IO) {
        if (!restoreCacheDirectory.isDirectory && !restoreCacheDirectory.mkdirs()) {
            throw RestoreException("Unable to create private restore storage.")
        }
        val candidate = File(restoreCacheDirectory, ".${UUID.randomUUID()}.bubu")
        val candidatePath = candidate.canonicalPath
        activeRestoreCandidates.add(candidatePath)
        try {
            val source = requireNotNull(applicationContext.contentResolver.openInputStream(Uri.parse(sourceUriString))) {
                "Selected backup cannot be opened."
            }
            source.use { input ->
                FileOutputStream(candidate).use { output ->
                    input.copyAtMost(output, MAX_CANDIDATE_BYTES)
                    output.fd.sync()
                }
            }
            // Copy the selected source first, including when it is itself a private cache URI.
            cleanupStaleRestoreCandidates()
            action(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RestoreException) {
            throw error
        } catch (error: Throwable) {
            throw RestoreException("Backup could not be read or validated.", error)
        } finally {
            try {
                candidate.delete()
            } finally {
                activeRestoreCandidates.remove(candidatePath)
            }
        }
    }

    private fun cleanupStaleRestoreCandidates() {
        val cutoff = System.currentTimeMillis() - RESTORE_CANDIDATE_GRACE_MILLIS
        restoreCacheDirectory.listFiles().orEmpty().forEach { file ->
            val identifier = file.name.removePrefix(".").removeSuffix(".$BACKUP_EXTENSION")
            if (file.name != ".$identifier.$BACKUP_EXTENSION" ||
                !runCatching { UUID.fromString(identifier).toString() == identifier }.getOrDefault(false)) return@forEach
            runCatching {
                val resolved = file.canonicalFile
                if (resolved.parentFile == restoreCacheDirectory.canonicalFile && resolved.name == file.name &&
                    resolved.path !in activeRestoreCandidates && resolved.isFile && resolved.lastModified() in 1L..cutoff) {
                    check(resolved.delete()) { "Unable to remove an abandoned restore candidate." }
                }
            }.onFailure { Log.w("OfflineRestoreRepo", "Restore candidate cleanup will retry later.", it) }
        }
    }

    private suspend fun createRecoveryBackup(): File = withContext(Dispatchers.IO) {
        if (!recoveryDirectory.isDirectory && !recoveryDirectory.mkdirs()) {
            throw RestoreException("Unable to create recovery backup storage.")
        }
        val identifier = UUID.randomUUID()
        val temporary = File(recoveryDirectory, ".recovery-$identifier.part")
        val recovery = File(recoveryDirectory, "recovery-$identifier.bubu")
        try {
            // The writer syncs its bytes and validates the archive with the formal reader.
            backupRepository.writePrivateBackup(temporary)
            if (!temporary.renameTo(recovery)) {
                throw RestoreException("Unable to publish the recovery backup.")
            }
            syncRecoveryDirectory()
        } finally {
            temporary.delete()
        }
        recoveryDirectory.listFiles()
            .orEmpty()
            .filter { file -> file != recovery && file.extension == BACKUP_EXTENSION }
            .forEach(File::delete)
        recovery
    }

    private fun syncRecoveryDirectory() {
        val descriptor = Os.open(
            recoveryDirectory.absolutePath,
            OsConstants.O_RDONLY,
            0,
        )
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private suspend fun ensureSpaceForRestore(candidate: File, preview: BackupPreview) {
        val requiredBytes = try {
            val recoveryBytes = backupRepository.privateBackupStorageBudget()
            val sqlite = database.openHelper.readableDatabase
            val pageCount = sqlite.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
            val pageSize = sqlite.query("PRAGMA page_size").use { it.moveToFirst(); it.getLong(0) }
            val currentDatabaseBytes = Math.multiplyExact(pageCount, pageSize)
            val walBytes = database.openHelper.databaseName?.let { name ->
                File(applicationContext.getDatabasePath(name).path + "-wal").length()
            } ?: 0L
            val incomingDataBytes = preview.manifest.files.single { it.relativePath == "data.json" }.sizeBytes
            // Budget data/index expansion and a transaction journal/WAL for either snapshot.
            // This is headroom, not a reservation against another process consuming disk.
            val databaseWorkspace = Math.addExact(
                Math.multiplyExact(maxOf(currentDatabaseBytes, Math.multiplyExact(incomingDataBytes, 4)), 2),
                Math.addExact(walBytes, SQLITE_RESTORE_HEADROOM_BYTES),
            )
            listOf(candidate.length(), preview.totalByteCount, recoveryBytes, databaseWorkspace)
                .fold(0L, Math::addExact)
        } catch (_: ArithmeticException) {
            throw RestoreException("Backup is too large to restore safely.")
        }
        require(availablePrivateBytes(restoreCacheDirectory) >= requiredBytes) {
            "Not enough private storage to create a recovery backup and restore this file."
        }
    }

    private fun latestRecoveryFile(): File? = recoveryDirectory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { file -> file.isFile && file.name.startsWith("recovery-") && file.extension == BACKUP_EXTENSION }
        .sortedByDescending(File::lastModified)
        .firstOrNull(BackupArchiveVerifier::isValid)

    private fun File.toRecoveryBackup() = RecoveryBackup(
        fileName = name,
        byteCount = length(),
        createdAtEpochMillis = lastModified(),
    )

    private suspend fun replaceCurrentData(archiveFile: File) {
        val preview = BackupArchiveReader.read(archiveFile)
        val stagedAttachments = stageAttachments(archiveFile, preview.data.attachments)
        val priorPaths: List<String>
        try {
            priorPaths = database.serviceAttachmentDao().getAllRelativePaths()
            val pendingPaths = database.pendingAttachmentDeletionDao().getAll().map { it.relativePath }
            database.withTransaction {
                database.clearAllTables()
                insertData(preview.data, stagedAttachments)
                (priorPaths + pendingPaths).distinct().forEach { path ->
                    database.pendingAttachmentDeletionDao().insertIgnore(
                        PendingAttachmentDeletionEntity(relativePath = path, createdAt = System.currentTimeMillis()),
                    )
                }
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                stagedAttachments.values.forEach { staged ->
                    runCatching {
                        // Cancellation may be delivered after SQLite has committed.
                        // Never delete a file still referenced by that committed snapshot.
                        if (database.serviceAttachmentDao().countByRelativePath(staged.relativePath) == 0) {
                            check(attachmentStore.deleteManagedFile(staged.relativePath)) {
                                "Unreferenced restore attachment cleanup must be retried."
                            }
                        }
                    }.onFailure { cleanupFailure ->
                        error.addSuppressed(cleanupFailure)
                        Log.w("OfflineRestoreRepo", "Restore attachment retained for safe cleanup retry.", cleanupFailure)
                    }
                }
            }
            throw error
        }
        cleanupPreviousAttachments()
    }

    private suspend fun cleanupPreviousAttachments() = withContext(NonCancellable) {
        try {
            database.pendingAttachmentDeletionDao().getAll().forEach { pending ->
                runCatching {
                    if (database.serviceAttachmentDao().countByRelativePath(pending.relativePath) > 0 ||
                        attachmentStore.deleteManagedFile(pending.relativePath)) {
                        database.pendingAttachmentDeletionDao().deleteById(pending.id)
                    }
                }.onFailure { error ->
                    Log.w("OfflineRestoreRepo", "Committed restore attachment cleanup will be retried.", error)
                }
            }
        } catch (error: Throwable) {
            Log.w("OfflineRestoreRepo", "Committed restore attachment cleanup remains pending.", error)
        }
    }

    private suspend fun stageAttachments(
        archiveFile: File,
        attachments: List<BackupAttachment>,
    ): Map<String, StagedServiceAttachment> = withContext(Dispatchers.IO) {
        ZipFile(archiveFile).use { archive ->
            val staged = mutableMapOf<String, StagedServiceAttachment>()
            try {
                attachments.forEach { attachment ->
                    val entry = requireNotNull(archive.getEntry(attachment.archivePath)) {
                        "Backup attachment is missing."
                    }
                    val bytes = archive.getInputStream(entry).use { input ->
                        input.readAtMost(MAX_ATTACHMENT_BYTES)
                    }
                    staged[attachment.publicId] = attachmentStore.restoreManagedBytes(
                        bytes = bytes,
                        displayName = attachment.displayName,
                        mimeType = attachment.mimeType,
                    )
                }
                staged
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    staged.values.forEach { attachmentStore.deleteManagedFile(it.relativePath) }
                }
                throw error
            }
        }
    }

    private suspend fun insertData(
        data: BackupData,
        stagedAttachments: Map<String, StagedServiceAttachment>,
    ) {
        val vehicleIds = data.vehicles.associate { vehicle ->
            vehicle.publicId to database.vehicleDao().insert(
                VehicleEntity(
                    publicId = vehicle.publicId,
                    name = vehicle.name,
                    vehicleType = VehicleType.valueOf(vehicle.vehicleType),
                    motorcycleClass = vehicle.motorcycleClass?.let(MotorcycleClass::valueOf),
                    brand = vehicle.brand,
                    model = vehicle.model,
                    manufactureYear = vehicle.manufactureYear,
                    engineDisplacementCc = vehicle.engineDisplacementCc,
                    licensePlate = vehicle.licensePlate,
                    powertrainType = vehicle.powertrainType?.let(PowertrainType::valueOf),
                    trackingStartDateEpochDay = vehicle.trackingStartDateEpochDay,
                    trackingStartOdometerKm = vehicle.trackingStartOdometerKm,
                    useNewVehicleMaintenanceBaseline = vehicle.useNewVehicleMaintenanceBaseline,
                    currentOdometerKm = vehicle.currentOdometerKm,
                    note = vehicle.note,
                    isArchived = vehicle.isArchived,
                    createdAt = vehicle.createdAt,
                    updatedAt = vehicle.updatedAt,
                    primaryInspectionMonthDay = vehicle.primaryInspectionMonthDay?.let(java.time.MonthDay::parse),
                    secondaryInspectionMonthDay = vehicle.secondaryInspectionMonthDay?.let(java.time.MonthDay::parse),
                ),
            )
        }
        data.fuelRecords.forEach { fuel ->
            database.fuelRecordDao().insert(
                FuelRecordEntity(
                    publicId = fuel.publicId,
                    vehicleId = vehicleIds.requireValue(fuel.vehiclePublicId),
                    dateEpochDay = fuel.dateEpochDay,
                    timeMinuteOfDay = fuel.timeMinuteOfDay,
                    sequenceInDay = fuel.sequenceInDay,
                    odometerKm = fuel.odometerKm,
                    fuelVolumeMl = fuel.fuelVolumeMl,
                    pricePerLiterMilli = fuel.pricePerLiterMilli,
                    totalCostTwd = fuel.totalCostTwd,
                    isFullTank = fuel.isFullTank,
                    fuelProduct = fuel.fuelProduct?.let(FuelProduct::valueOf),
                    fuelingMode = FuelingMode.valueOf(fuel.fuelingMode),
                    fuelEconomyStatisticsStatus = com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus.valueOf(fuel.fuelEconomyStatisticsStatus),
                    note = fuel.note,
                    createdAt = fuel.createdAt,
                    updatedAt = fuel.updatedAt,
                ),
            )
        }
        val serviceTypeIds = data.serviceTypes.associate { type ->
            type.publicId to database.serviceTypeDao().insert(
                ServiceTypeEntity(
                    publicId = type.publicId,
                    name = type.name,
                    vehicleType = com.kumo.bubu.domain.model.VehicleType.valueOf(type.vehicleType),
                    isBuiltIn = type.isBuiltIn,
                    isArchived = type.isArchived,
                    sortOrder = type.sortOrder,
                    createdAt = type.createdAt,
                    updatedAt = type.updatedAt,
                ),
            )
        }
        val serviceRecordIds = data.serviceRecords.associate { record ->
            record.publicId to database.serviceRecordDao().insert(
                ServiceRecordEntity(
                    publicId = record.publicId,
                    vehicleId = vehicleIds.requireValue(record.vehiclePublicId),
                    dateEpochDay = record.dateEpochDay,
                    timeMinuteOfDay = record.timeMinuteOfDay,
                    sequenceInDay = record.sequenceInDay,
                    odometerKm = record.odometerKm,
                    recordType = ServiceRecordType.valueOf(record.recordType),
                    title = record.title,
                    paymentMethod = record.paymentMethod?.let(PaymentMethod::valueOf),
                    totalCostTwd = record.totalCostTwd,
                    note = record.note,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt,
                ),
            )
        }
        data.serviceReminderPreferences.forEach { preference ->
            database.vehicleServiceReminderPreferenceDao().insert(
                VehicleServiceReminderPreferenceEntity(
                    publicId = preference.publicId,
                    vehicleId = vehicleIds.requireValue(preference.vehiclePublicId),
                    serviceTypeId = serviceTypeIds.requireValue(preference.serviceTypePublicId),
                    isEnabled = preference.isEnabled,
                    intervalKm = preference.intervalKm,
                    intervalMonths = preference.intervalMonths,
                    reminderType = com.kumo.bubu.domain.model.MaintenanceReminderType.valueOf(preference.reminderType),
                    baseOdometerKm = preference.baseOdometerKm,
                    sortOrder = preference.sortOrder,
                    createdAt = preference.createdAt,
                    updatedAt = preference.updatedAt,
                ),
            )
        }
        val serviceItemIds = data.serviceItems.associate { item ->
            item.publicId to database.serviceItemDao().insertAll(
                listOf(
                    ServiceItemEntity(
                        publicId = item.publicId,
                        serviceRecordId = serviceRecordIds.requireValue(item.serviceRecordPublicId),
                        serviceTypeId = item.serviceTypePublicId?.let { publicId ->
                            serviceTypeIds.requireValue(publicId)
                        },
                        sequenceInRecord = item.sequenceInRecord,
                        nameSnapshot = item.nameSnapshot,
                        quantityMilli = item.quantityMilli,
                        quantityUnit = ServiceQuantityUnit.valueOf(item.quantityUnit),
                        unitPriceTwd = item.unitPriceTwd,
                        subtotalTwd = item.subtotalTwd,
                        nextDueOdometerKm = item.nextDueOdometerKm,
                        nextDueDateEpochDay = item.nextDueDateEpochDay,
                        note = item.note,
                        createdAt = item.createdAt,
                        updatedAt = item.updatedAt,
                    ),
                ),
            ).single()
        }
        val reminderEntities = data.reminders.associate { reminder ->
            reminder.publicId to VehicleReminderEntity(
                publicId = reminder.publicId,
                vehicleId = vehicleIds.requireValue(reminder.vehiclePublicId),
                source = ReminderSource.valueOf(reminder.source),
                sourceServiceItemId = reminder.sourceServiceItemPublicId?.let { publicId ->
                    serviceItemIds.requireValue(publicId)
                },
                title = reminder.title,
                dueOdometerKm = reminder.dueOdometerKm,
                dueDateEpochDay = reminder.dueDateEpochDay,
                completedByServiceRecordId = reminder.completedByServiceRecordPublicId?.let { publicId ->
                    serviceRecordIds.requireValue(publicId)
                },
                completedAt = reminder.completedAt,
                snoozedUntilEpochDay = reminder.snoozedUntilEpochDay,
                lastNotifiedStatus = reminder.lastNotifiedStatus?.let(ReminderStatus::valueOf),
                isEnabled = reminder.isEnabled,
                createdAt = reminder.createdAt,
                updatedAt = reminder.updatedAt,
                automaticKey = reminder.automaticKey,
                ruleVersion = reminder.ruleVersion,
                ruleVerifiedEpochDay = reminder.ruleVerifiedEpochDay,
                estimatedNotificationEpochDay = reminder.estimatedNotificationEpochDay,
                lastNotifiedTrigger = reminder.lastNotifiedTrigger,
                referenceDateEpochDay = reminder.referenceDateEpochDay,
            )
        }
        val reminderIds = reminderEntities.mapValues { (_, entity) -> database.vehicleReminderDao().insert(entity) }
        val expenseIds = data.expenseRecords.associate { expense ->
            expense.publicId to database.expenseRecordDao().insert(
                ExpenseRecordEntity(
                    publicId = expense.publicId,
                    vehicleId = vehicleIds.requireValue(expense.vehiclePublicId),
                    dateEpochDay = expense.dateEpochDay,
                    timeMinuteOfDay = expense.timeMinuteOfDay,
                    sequenceInDay = expense.sequenceInDay,
                    category = ExpenseCategory.valueOf(expense.category),
                    totalCostTwd = expense.totalCostTwd,
                    note = expense.note,
                    createdAt = expense.createdAt,
                    updatedAt = expense.updatedAt,
                    completedReminderId = expense.completedReminderPublicId?.let { publicId ->
                        reminderIds.requireValue(publicId)
                    },
                ),
            )
        }
        data.reminders.forEach { reminder ->
            val entity = reminderEntities.requireValue(reminder.publicId).copy(
                id = reminderIds.requireValue(reminder.publicId),
                completedByExpenseRecordId = reminder.completedByExpenseRecordPublicId?.let { publicId ->
                    expenseIds.requireValue(publicId)
                },
            )
            database.vehicleReminderDao().update(entity)
        }
        data.attachments.forEach { attachment ->
            val staged = stagedAttachments.requireValue(attachment.publicId)
            database.serviceAttachmentDao().insert(
                ServiceAttachmentEntity(
                    publicId = attachment.publicId,
                    serviceRecordId = serviceRecordIds.requireValue(attachment.serviceRecordPublicId),
                    sequenceInRecord = attachment.sequenceInRecord,
                    relativePath = staged.relativePath,
                    displayName = staged.displayName,
                    mimeType = staged.mimeType,
                    createdAt = attachment.createdAt,
                    updatedAt = attachment.updatedAt,
                ),
            )
        }
    }

    private fun BackupPreview.toDomain() = RestorePreview(
        createdAtEpochMillis = manifest.createdAtEpochMillis,
        appVersion = manifest.appVersion,
        vehicleCount = data.vehicles.size,
        fuelRecordCount = data.fuelRecords.size,
        serviceRecordCount = data.serviceRecords.size,
        serviceItemCount = data.serviceItems.size,
        expenseRecordCount = data.expenseRecords.size,
        reminderCount = data.reminders.size,
        attachmentCount = data.attachments.size,
        totalByteCount = totalByteCount,
    )

    private fun <K, V> Map<K, V>.requireValue(key: K): V =
        requireNotNull(get(key)) { "Backup relation is missing." }

    private fun InputStream.copyAtMost(destination: FileOutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) { "Backup is too large to restore safely." }
            destination.write(buffer, 0, read)
        }
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) { "Backup attachment is too large to restore safely." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        // Register before creation; release only after cleanup. Shared across repository instances.
        val activeRestoreCandidates = ConcurrentHashMap.newKeySet<String>()
        const val RESTORE_CACHE_DIRECTORY = "restore"
        const val RECOVERY_DIRECTORY = "recovery-backups"
        const val BACKUP_EXTENSION = "bubu"
        const val MAX_CANDIDATE_BYTES = BackupArchiveLimits.MAX_ARCHIVE_BYTES
        const val MAX_ATTACHMENT_BYTES = BackupArchiveLimits.MAX_ATTACHMENT_BYTES
        const val SQLITE_RESTORE_HEADROOM_BYTES = 16L * 1024 * 1024
        const val RESTORE_CANDIDATE_GRACE_MILLIS = 24L * 60 * 60 * 1000
    }
}

class RestoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
