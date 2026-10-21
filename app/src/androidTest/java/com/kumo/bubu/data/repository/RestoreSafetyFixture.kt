package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.local.entity.*
import com.kumo.bubu.domain.model.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executor

/** Real isolated storage; assertions observe exported archives, never DAO state. */
internal class RestoreSafetyFixture(
    val context: Context,
    diskBackedTarget: Boolean = false,
    journalMode: RoomDatabase.JournalMode = RoomDatabase.JournalMode.TRUNCATE,
) {
    val source = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).build()
    val targetName = if (diskBackedTarget) "restore-safety-${UUID.randomUUID()}.db" else null
    @Volatile var beforeTargetQuery: ((String, List<Any?>) -> Unit)? = null
    val target = (targetName?.let { Room.databaseBuilder(context, BuBuDatabase::class.java, it)
        .setJournalMode(journalMode) }
        ?: Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java))
        .setQueryCallback(RoomDatabase.QueryCallback { sql, arguments ->
            beforeTargetQuery?.invoke(sql, arguments)
        }, Executor { it.run() })
        .build()
    val directory = File(context.cacheDir, "restore-safety-${UUID.randomUUID()}").apply { mkdirs() }
    val recoveryDirectory = File(directory, "recovery").apply { mkdirs() }
    @Volatile var beforeRestoredWrite: ((File) -> Unit)? = null
    @Volatile var onDeleteManagedFile: ((File) -> Boolean)? = null
    val store = PrivateAttachmentStore(context, "attachment", openRestoreOutput = { file ->
        object : FileOutputStream(file) {
            override fun write(bytes: ByteArray) {
                beforeRestoredWrite?.invoke(file)
                super.write(bytes)
            }
        }
    }, deleteFile = { file -> onDeleteManagedFile?.invoke(file) ?: file.delete() })
    private val paths = mutableSetOf<String>()
    private var initialPaths = emptySet<String>()

    suspend fun rememberExistingFiles() {
        initialPaths = store.listManagedRelativePaths().toSet()
    }

    suspend fun seed(
        database: BuBuDatabase,
        prefix: String,
        attachmentCount: Int = 1,
        identity: (String) -> String = { it },
    ) {
        val vehicle = database.vehicleDao().insert(VehicleEntity(
            publicId = identity("$prefix-vehicle"), name = prefix, vehicleType = VehicleType.CAR,
            motorcycleClass = null, brand = null, model = null, manufactureYear = 2020,
            engineDisplacementCc = 1800, licensePlate = null, powertrainType = PowertrainType.GASOLINE,
            trackingStartDateEpochDay = 20_000, trackingStartOdometerKm = 0, currentOdometerKm = 1200,
            note = "retained $prefix", isArchived = false, createdAt = 100, updatedAt = 200,
        ))
        database.fuelRecordDao().insert(FuelRecordEntity(
            publicId = identity("$prefix-fuel"), vehicleId = vehicle, dateEpochDay = 20_001,
            timeMinuteOfDay = null, sequenceInDay = 0, odometerKm = 1200, fuelVolumeMl = 4270,
            pricePerLiterMilli = null, totalCostTwd = 135, isFullTank = true,
            fuelProduct = FuelProduct.GASOLINE_95, note = null, createdAt = 100, updatedAt = 200,
        ))
        val type = database.serviceTypeDao().insert(ServiceTypeEntity(
            publicId = identity("$prefix-type"), name = prefix, vehicleType = VehicleType.CAR,
            isBuiltIn = false, isArchived = false, sortOrder = 2, createdAt = 100, updatedAt = 200,
        ))
        val record = database.serviceRecordDao().insert(ServiceRecordEntity(
            publicId = identity("$prefix-service"), vehicleId = vehicle, dateEpochDay = 20_000,
            timeMinuteOfDay = null, sequenceInDay = 0, odometerKm = 1100,
            recordType = ServiceRecordType.MAINTENANCE, title = prefix, paymentMethod = PaymentMethod.CASH,
            totalCostTwd = 600, note = prefix, createdAt = 100, updatedAt = 200,
        ))
        val item = database.serviceItemDao().insertAll(listOf(ServiceItemEntity(
            publicId = identity("$prefix-item"), serviceRecordId = record, serviceTypeId = type, sequenceInRecord = 0,
            nameSnapshot = prefix, quantityMilli = 1500, quantityUnit = ServiceQuantityUnit.LITER,
            unitPriceTwd = 400, subtotalTwd = 600, nextDueOdometerKm = 5000,
            nextDueDateEpochDay = 20_180, note = null, createdAt = 100, updatedAt = 200,
        ))).single()
        database.vehicleReminderDao().insert(VehicleReminderEntity(
            publicId = identity("$prefix-reminder"), vehicleId = vehicle, source = ReminderSource.SERVICE_ITEM,
            sourceServiceItemId = item, title = prefix, dueOdometerKm = 5000, dueDateEpochDay = 20_180,
            isEnabled = true, createdAt = 100, updatedAt = 200,
        ))
        database.expenseRecordDao().insert(ExpenseRecordEntity(
            publicId = identity("$prefix-expense"), vehicleId = vehicle, dateEpochDay = 20_000,
            timeMinuteOfDay = null, sequenceInDay = 0, category = ExpenseCategory.INSURANCE,
            totalCostTwd = 300, note = prefix, createdAt = 100, updatedAt = 200,
        ))
        database.vehicleServiceReminderPreferenceDao().insert(VehicleServiceReminderPreferenceEntity(
            publicId = identity("$prefix-policy"), vehicleId = vehicle, serviceTypeId = type,
            isEnabled = false, intervalKm = 5000, intervalMonths = 3,
            reminderType = MaintenanceReminderType.INSPECT, baseOdometerKm = 0,
            sortOrder = 3, createdAt = 100, updatedAt = 200,
        ))
        val bytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT,
        ) + prefix.encodeToByteArray()
        repeat(attachmentCount) { index ->
            val attachment = store.restoreManagedBytes(bytes + index.toByte(), "$prefix-$index.png", "image/png")
            paths += attachment.relativePath
            database.serviceAttachmentDao().insert(ServiceAttachmentEntity(
                publicId = identity("$prefix-attachment-$index"), serviceRecordId = record, sequenceInRecord = index,
                relativePath = attachment.relativePath, displayName = attachment.displayName,
                mimeType = attachment.mimeType, createdAt = 100, updatedAt = 200,
            ))
        }
    }

    suspend fun export(database: BuBuDatabase, name: String): File = File(directory, name).also {
        OfflineBackupRepository(context, database, store).createBackup(Uri.fromFile(it).toString())
    }

    fun repository(backup: OfflineBackupRepository = OfflineBackupRepository(context, target, store)) =
        OfflineRestoreRepository(context, target, store, backup, recoveryDirectory)

    fun assertSameSnapshot(expected: File, actual: File) {
        assertBackupArchiveEquals(expected, actual)
    }

    suspend fun close() {
        beforeTargetQuery = null
        beforeRestoredWrite = null
        onDeleteManagedFile = null
        paths += store.listManagedRelativePaths().toSet() - initialPaths
        paths += source.serviceAttachmentDao().getAllRelativePaths()
        paths += target.serviceAttachmentDao().getAllRelativePaths()
        paths.forEach { store.deleteManagedFile(it) }
        source.close()
        target.close()
        targetName?.let(context::deleteDatabase)
        directory.deleteRecursively()
    }
}
