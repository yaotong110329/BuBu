package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kumo.bubu.feature.settings.RestoreError
import com.kumo.bubu.feature.settings.RestoreViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.backup.*
import com.kumo.bubu.data.local.entity.*
import com.kumo.bubu.domain.model.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.kumo.bubu.domain.repository.BackupTooLargeException
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Public backup/restore boundary: compare re-exported semantics, never local row IDs. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripAuditTest {
    private lateinit var context: Context
    private lateinit var source: BuBuDatabase
    private lateinit var target: BuBuDatabase
    private lateinit var directory: File
    private lateinit var store: PrivateAttachmentStore
    private val managedPaths = mutableSetOf<String>()
    private val date = 20_000L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        source = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).build()
        directory = File(context.cacheDir, "round-trip-audit-${UUID.randomUUID()}").apply { mkdirs() }
        store = PrivateAttachmentStore(context, "attachment")
    }

    @After fun tearDown() = runBlocking<Unit> {
        managedPaths += source.serviceAttachmentDao().getAllRelativePaths()
        managedPaths += target.serviceAttachmentDao().getAllRelativePaths()
        managedPaths.forEach { store.deleteManagedFile(it) }
        source.close()
        target.close()
        directory.deleteRecursively()
    }

    @Test fun replacementRoundTripPreservesEveryBackedUpCollectionAndAttachmentBytes() = runBlocking {
        seed(source, "source")
        seed(target, "old")
        val before = backup(source, "source.bubu")
        restore().restore(Uri.fromFile(before).toString())
        val after = backup(target, "restored.bubu")

        assertArchiveSemanticsEqual(before, after)
        val data = BackupArchiveReader.read(after).data
        assertEquals(2, data.vehicles.size)
        assertTrue(data.vehicles.any { it.isArchived })
        val vehicle = data.vehicles.single { it.publicId == identity("source-vehicle") }
        assertEquals(2020, vehicle.manufactureYear)
        assertEquals(1800, vehicle.engineDisplacementCc)
        assertEquals("line1\nline2", vehicle.note)
        assertEquals(java.time.MonthDay.of(2, 29).toString(), vehicle.primaryInspectionMonthDay)
        assertEquals(0L, vehicle.trackingStartOdometerKm)
        assertTrue(vehicle.useNewVehicleMaintenanceBaseline)
        val includedFuel = data.fuelRecords.single { it.publicId == identity("source-fuel-0") }
        val excludedFuel = data.fuelRecords.single { it.publicId == identity("source-fuel-1") }
        assertNull(includedFuel.timeMinuteOfDay)
        assertNull(includedFuel.pricePerLiterMilli)
        assertEquals(4_270L, includedFuel.fuelVolumeMl)
        assertEquals(135L, includedFuel.totalCostTwd)
        assertEquals(31_700L, excludedFuel.pricePerLiterMilli)
        assertEquals(600L, data.serviceRecords.single().totalCostTwd)
        assertEquals(1_500L, data.serviceItems.single().quantityMilli)
        assertEquals(400L, data.serviceItems.single().unitPriceTwd)
        assertEquals(600L, data.serviceItems.single().subtotalTwd)
        assertEquals(0L, data.expenseRecords.single().totalCostTwd)
        assertEquals("收據😀.png", data.attachments.single().displayName)
        assertEquals(MaintenanceReminderType.INSPECT.name, data.serviceReminderPreferences.single().reminderType)
        assertEquals(3L, data.serviceReminderPreferences.single().intervalMonths)
        assertEquals(FuelEconomyStatisticsStatus.EXCLUDED.name, excludedFuel.fuelEconomyStatisticsStatus)
        assertTrue(data.vehicles.none { it.publicId == identity("old-vehicle") })
        // BackupData's public contract has no application preference/credential payload.
        val json = Json.encodeToString(BackupData.serializer(), data)
        assertFalse(json.contains("accessToken"))
        assertFalse(json.contains("lastCloudBackupAt"))
        assertFalse(json.contains("backupReminderEnabled"))
    }

    @Test fun failureAfterWorkOrdersAndBeforeRemindersRecoversCompleteOldSnapshot() = runBlocking {
        checkFailedReplacement(late = false)
    }

    @Test fun failureAfterRemindersAndBeforeAttachmentsRecoversCompleteOldSnapshot() = runBlocking {
        checkFailedReplacement(late = true)
    }

    @Test fun interruptedRecoveryWriteKeepsPreviousExportableBackupAndCurrentSnapshot() = runBlocking {
        seed(source, "new")
        seed(target, "old")
        val candidate = backup(source, "candidate.bubu")
        val recoveryDirectory = File(directory, "recovery").apply { mkdirs() }
        val previous = File(recoveryDirectory, "recovery-previous.bubu")
        OfflineBackupRepository(context, target, store).writePrivateBackup(previous)
        assertTrue(previous.setLastModified(1_000))
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val restoreRepository = OfflineRestoreRepository(
            context, target, store,
            OfflineBackupRepository(context, target, store, openArchiveOutput = { file ->
                interruptAfterOpeningArchive(file, writeStarted, releaseWrite)
            }),
            recoveryDirectory,
        )

        val restoring = async(Dispatchers.IO) {
            runCatching { restoreRepository.restore(Uri.fromFile(candidate).toString()) }
        }
        assertTrue(writeStarted.await(20, TimeUnit.SECONDS))
        try {
            assertEquals(previous.name, restoreRepository.getLatestRecoveryBackup()?.fileName)
        } finally {
            releaseWrite.countDown()
        }
        val failure = restoring.await()

        assertTrue(failure.isFailure)
        assertEquals(previous.name, restoreRepository.getLatestRecoveryBackup()?.fileName)
        val exported = File(directory, "exported-previous.bubu")
        restoreRepository.exportLatestRecoveryBackup(Uri.fromFile(exported).toString())
        assertArchiveSemanticsEqual(previous, exported)
        assertArchiveSemanticsEqual(previous, backup(target, "after-interruption.bubu"))
    }

    @Test fun oversizedSnapshotRefusesLocalAndRecoveryBackupWithoutChangingCurrentData() = runBlocking {
        seed(source, "source")
        seed(target, "candidate")
        val original = backup(source, "before-size-rejection.bubu")
        val candidate = backup(target, "small-candidate.bubu")
        val record = source.serviceRecordDao().getAllForExport().single()
        val item = source.serviceItemDao().getAllForExport().single()
        val attachment = source.serviceAttachmentDao().getAllForExport().single()
        val image = requireNotNull(store.readManagedBytes(attachment.relativePath))
        val addedRecords = mutableListOf<Long>()
        repeat(26) { index ->
            val added = source.serviceRecordDao().insert(record.copy(
                id = 0, publicId = identity("size-record-$index"), sequenceInDay = index + 4,
            ))
            addedRecords += added
            source.serviceItemDao().insertAll(listOf(item.copy(
                id = 0, publicId = identity("size-item-$index"), serviceRecordId = added,
            )))
            val path = "attachments/service/${UUID.randomUUID()}.png"
            managedPaths += path
            RandomAccessFile(File(context.filesDir, path), "rw").use { file ->
                file.write(image)
                file.setLength(20L * 1024 * 1024)
            }
            source.serviceAttachmentDao().insert(attachment.copy(
                id = 0, publicId = identity("size-attachment-$index"), serviceRecordId = added,
                relativePath = path,
            ))
        }
        val rejected = File(directory, "rejected.bubu")
        val result = runCatching {
            OfflineBackupRepository(context, source, store).createBackup(Uri.fromFile(rejected).toString())
        }
        assertTrue(result.exceptionOrNull() is BackupTooLargeException)
        assertFalse(rejected.exists())

        val recoveryDirectory = File(directory, "oversized-recovery")
        val restoreResult = runCatching {
            OfflineRestoreRepository(context, source, store, OfflineBackupRepository(context, source, store),
                recoveryDirectory).restore(Uri.fromFile(candidate).toString())
        }
        assertTrue(restoreResult.exceptionOrNull() is RestoreException)
        assertTrue(restoreResult.exceptionOrNull()?.cause is BackupTooLargeException)
        assertTrue(recoveryDirectory.listFiles().orEmpty().isEmpty())
        // Remove only the rows introduced by this fixture, then observe the original
        // snapshot via the same backup/reader boundary. A destructive clear cannot hide here.
        addedRecords.forEach { source.serviceRecordDao().deleteById(it) }
        assertArchiveSemanticsEqual(original, backup(source, "after-size-rejection.bubu"))
    }

    @Test fun invalidSemanticsFailBeforeRecoveryAndKeepTheSnapshotAndAttachmentBytes() = runBlocking {
        seed(source, "source")
        seed(target, "old")
        val candidate = exportArchive(source, "valid-semantic-candidate.bubu")
        val original = exportArchive(target, "original-semantic-state.bubu")
        val repository = restore()
        val recoveryFile = File(directory, "recovery/recovery-semantic-original.bubu")
        recoveryFile.parentFile?.mkdirs()
        original.copyTo(recoveryFile)
        val recovery = repository.getLatestRecoveryBackup()
        assertNotNull(recovery)
        val exportedRecovery = File(directory, "recovery-before.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(exportedRecovery).toString())
        val recoveryBytes = exportedRecovery.readBytes()
        val preview = BackupArchiveReader.read(candidate)
        val data = preview.data
        val archiveContents = ZipFile(candidate).use { zip ->
            preview.manifest.files.associate { metadata ->
                metadata.relativePath to zip.getInputStream(zip.getEntry(metadata.relativePath)).use { it.readBytes() }
            }
        }
        val invalid = listOf(
            data.copy(serviceRecords = data.serviceRecords.map { it.copy(totalCostTwd = -1) }),
            data.copy(serviceRecords = data.serviceRecords.map { it.copy(dateEpochDay = Long.MAX_VALUE) }),
            data.copy(fuelRecords = data.fuelRecords.map { it.copy(fuelProduct = "UNKNOWN") }),
            data.copy(reminders = data.reminders.map { if (it.source == "SERVICE_ITEM") it.copy(vehiclePublicId = identity("source-archived")) else it }),
            data.copy(serviceReminderPreferences = data.serviceReminderPreferences + data.serviceReminderPreferences.single().copy(publicId = identity("duplicate-policy"))),
        )
        invalid.forEachIndexed { index, invalidData ->
            val selected = File(directory, "invalid-semantic-$index.bubu")
            val contents = archiveContents.map { (path, bytes) ->
                BackupFileContent(path, if (path == "data.json") Json.encodeToString(BackupData.serializer(), invalidData).encodeToByteArray() else bytes)
            }
            BackupArchiveWriter.write(selected, "1.1.1", 1, preview.manifest.recordCounts, contents)
            val selectedBytes = selected.readBytes()
            val uri = Uri.fromFile(selected).toString()
            assertTrue(runCatching { repository.preview(uri) }.isFailure)
            assertTrue(runCatching { repository.restore(uri) }.isFailure)

            val models = ViewModelStore()
            lateinit var viewModel: RestoreViewModel
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                viewModel = ViewModelProvider(models, RestoreViewModel.factory(repository))[RestoreViewModel::class.java]
                viewModel.preview(uri)
            }
            try {
                val state = withTimeout(10_000) { viewModel.uiState.first { it.error != null && !it.isLoadingPreview } }
                assertEquals(RestoreError.INVALID_BACKUP, state.error)
                assertNull(state.preview)
            } finally {
                instrumentation.runOnMainSync { models.clear() }
            }
            assertArrayEquals(selectedBytes, selected.readBytes())
            assertArchiveSemanticsEqual(original, exportArchive(target, "after-semantic-$index.bubu"))
            assertEquals(recovery, repository.getLatestRecoveryBackup())
            val exported = File(directory, "recovery-after-$index.bubu")
            repository.exportLatestRecoveryBackup(Uri.fromFile(exported).toString())
            assertArrayEquals(recoveryBytes, exported.readBytes())
        }
    }

    private suspend fun exportArchive(database: BuBuDatabase, name: String): File = File(directory, name).also {
        OfflineBackupRepository(context, database, store).createBackup(Uri.fromFile(it).toString())
    }

    private suspend fun checkFailedReplacement(late: Boolean) {
        seed(source, "source")
        seed(target, "old")
        val candidate = backup(source, "candidate.bubu")
        val original = backup(target, "original.bubu")
        // Keep the archive semantically valid; fail only the fixture database after earlier inserts.
        val table = if (late) "service_attachments" else "vehicle_reminders"
        val rejectedId = identity(if (late) "source-attachment" else "source-reminder")
        target.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_fixture_restore BEFORE INSERT ON $table " +
                "WHEN NEW.publicId = '$rejectedId' BEGIN SELECT RAISE(ABORT, 'Injected restore failure'); END",
        )
        val result = runCatching { restore().restore(Uri.fromFile(candidate).toString()) }
        assertTrue(result.exceptionOrNull() is RestoreException)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("previous data was recovered"))
        assertArchiveSemanticsEqual(original, backup(target, "after-failure.bubu"))
        val recovery = File(directory, "recovery").listFiles().orEmpty().single { it.extension == "bubu" }
        assertArchiveSemanticsEqual(original, recovery)
    }

    private fun restore() = OfflineRestoreRepository(context, target, store,
        OfflineBackupRepository(context, target, store), File(directory, "recovery"))

    private fun interruptAfterOpeningArchive(
        file: File,
        writeStarted: CountDownLatch,
        releaseWrite: CountDownLatch,
    ): FileOutputStream = object : FileOutputStream(file) {
        private var bytesRemaining = 64

        override fun write(value: Int) {
            if (bytesRemaining == 0) interruptWrite()
            super.write(value)
            bytesRemaining--
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (bytesRemaining == 0) interruptWrite()
            val written = minOf(bytesRemaining, length)
            if (written > 0) super.write(bytes, offset, written)
            bytesRemaining -= written
            if (written < length) interruptWrite()
        }

        private fun interruptWrite(): Nothing {
            writeStarted.countDown()
            check(releaseWrite.await(20, TimeUnit.SECONDS)) { "Test did not release the interrupted write." }
            throw IOException("Injected short write")
        }
    }

    private suspend fun backup(database: BuBuDatabase, name: String): File = File(directory, name).also {
        OfflineBackupRepository(context, database, store).writePrivateBackup(it)
    }

    private fun assertArchiveSemanticsEqual(expected: File, actual: File) {
        val left = BackupArchiveReader.read(expected).data
        val right = BackupArchiveReader.read(actual).data
        assertEquals(left.normalized(), right.normalized())
        ZipFile(expected).use { a -> ZipFile(actual).use { b ->
            left.attachments.forEach { attachment ->
                assertArrayEquals(a.getInputStream(a.getEntry(attachment.archivePath)).use { it.readBytes() },
                    b.getInputStream(b.getEntry(attachment.archivePath)).use { it.readBytes() })
            }
        } }
    }

    private fun BackupData.normalized() = copy(
        vehicles = vehicles.sortedBy { it.publicId }, fuelRecords = fuelRecords.sortedBy { it.publicId },
        serviceTypes = serviceTypes.sortedBy { it.publicId }, serviceRecords = serviceRecords.sortedBy { it.publicId },
        serviceItems = serviceItems.sortedBy { it.publicId }, expenseRecords = expenseRecords.sortedBy { it.publicId },
        reminders = reminders.sortedBy { it.publicId }, serviceReminderPreferences = serviceReminderPreferences.sortedBy { it.publicId },
        attachments = attachments.sortedBy { it.publicId },
    )

    private fun identity(label: String) = UUID.nameUUIDFromBytes(label.encodeToByteArray()).toString()

    private suspend fun seed(database: BuBuDatabase, prefix: String) {
        val vehicle = database.vehicleDao().insert(VehicleEntity(
            publicId = identity("$prefix-vehicle"), name = "車😀", vehicleType = VehicleType.CAR,
            motorcycleClass = null, brand = "品牌", model = null, manufactureYear = 2020,
            engineDisplacementCc = 1800, licensePlate = "TEST", powertrainType = PowertrainType.GASOLINE,
            trackingStartDateEpochDay = date - 1, trackingStartOdometerKm = 0, currentOdometerKm = 1200,
            useNewVehicleMaintenanceBaseline = true, note = "line1\nline2", isArchived = false,
            createdAt = 100, updatedAt = 200, primaryInspectionMonthDay = java.time.MonthDay.of(2, 29),
        ))
        database.vehicleDao().insert(VehicleEntity(
            publicId = identity("$prefix-archived"), name = "封存機車", vehicleType = VehicleType.MOTORCYCLE,
            motorcycleClass = MotorcycleClass.ORDINARY_HEAVY, brand = null, model = null, manufactureYear = null,
            engineDisplacementCc = null, licensePlate = null, powertrainType = null, trackingStartDateEpochDay = date,
            trackingStartOdometerKm = 0, currentOdometerKm = 0, note = null, isArchived = true, createdAt = 100, updatedAt = 200,
        ))
        repeat(2) { index -> database.fuelRecordDao().insert(FuelRecordEntity(
            publicId = identity("$prefix-fuel-$index"), vehicleId = vehicle, dateEpochDay = date + index,
            timeMinuteOfDay = if (index == 0) null else 600, sequenceInDay = index, odometerKm = 1000L + index * 200,
            fuelVolumeMl = 4270, pricePerLiterMilli = if (index == 0) null else 31700, totalCostTwd = 135,
            isFullTank = true, fuelProduct = FuelProduct.GASOLINE_95, fuelingMode = FuelingMode.SELF_SERVICE,
            fuelEconomyStatisticsStatus = if (index == 0) FuelEconomyStatisticsStatus.INCLUDED else FuelEconomyStatisticsStatus.EXCLUDED,
            note = null, createdAt = 100, updatedAt = 200,
        )) }
        val type = database.serviceTypeDao().insert(ServiceTypeEntity(
            publicId = identity("$prefix-type"), name = "自訂保養", vehicleType = VehicleType.CAR,
            isBuiltIn = false, isArchived = false, sortOrder = 2, createdAt = 100, updatedAt = 200,
        ))
        val record = database.serviceRecordDao().insert(ServiceRecordEntity(
            publicId = identity("$prefix-service"), vehicleId = vehicle, dateEpochDay = date,
            timeMinuteOfDay = null, sequenceInDay = 3, odometerKm = 1100, recordType = ServiceRecordType.MAINTENANCE,
            title = "工單", paymentMethod = PaymentMethod.CASH, totalCostTwd = 600, note = "保留歷史欄位", createdAt = 100, updatedAt = 200,
        ))
        val item = database.serviceItemDao().insertAll(listOf(ServiceItemEntity(
            publicId = identity("$prefix-item"), serviceRecordId = record, serviceTypeId = type, sequenceInRecord = 0,
            nameSnapshot = "當時名稱", quantityMilli = 1500, quantityUnit = ServiceQuantityUnit.LITER, unitPriceTwd = 400,
            subtotalTwd = 600, nextDueOdometerKm = 5000, nextDueDateEpochDay = date + 180,
            note = "項目", createdAt = 100, updatedAt = 200,
        ))).single()
        // Raw DB fixture tests serialized links, not chronological completion-chain regeneration.
        val reminder = database.vehicleReminderDao().insert(VehicleReminderEntity(
            publicId = identity("$prefix-reminder"), vehicleId = vehicle, source = ReminderSource.SERVICE_ITEM,
            sourceServiceItemId = item, title = "提醒", dueOdometerKm = 5000, dueDateEpochDay = date + 180,
            completedByServiceRecordId = record, completedAt = 200, snoozedUntilEpochDay = date + 3,
            lastNotifiedStatus = ReminderStatus.DUE_SOON, isEnabled = true, createdAt = 100, updatedAt = 200,
            ruleVersion = 1, ruleVerifiedEpochDay = date, estimatedNotificationEpochDay = date + 170,
            lastNotifiedTrigger = "status:DUE_SOON", referenceDateEpochDay = date,
        ))
        val expenseReminder = database.vehicleReminderDao().insert(
            requireNotNull(database.vehicleReminderDao().getById(reminder)).copy(
                id = 0, publicId = identity("$prefix-expense-reminder"), source = ReminderSource.MANUAL,
                sourceServiceItemId = null, completedByServiceRecordId = null, title = "保險提醒",
            ),
        )
        val expense = database.expenseRecordDao().insert(ExpenseRecordEntity(
            publicId = identity("$prefix-expense"), vehicleId = vehicle, dateEpochDay = date, timeMinuteOfDay = 120,
            sequenceInDay = 1, category = ExpenseCategory.INSURANCE, totalCostTwd = 0, note = null,
            createdAt = 100, updatedAt = 200, completedReminderId = expenseReminder,
        ))
        database.vehicleReminderDao().getById(expenseReminder)?.let {
            database.vehicleReminderDao().update(it.copy(completedByExpenseRecordId = expense))
        }
        database.vehicleServiceReminderPreferenceDao().insert(VehicleServiceReminderPreferenceEntity(
            publicId = identity("$prefix-policy"), vehicleId = vehicle, serviceTypeId = type,
            isEnabled = false, intervalKm = 5000, intervalMonths = 3, reminderType = MaintenanceReminderType.INSPECT,
            baseOdometerKm = 0, sortOrder = 3, createdAt = 100, updatedAt = 200,
        ))
        val bytes = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==", android.util.Base64.DEFAULT)
        val staged = store.restoreManagedBytes(bytes, "收據😀.png", "image/png")
        managedPaths += staged.relativePath
        database.serviceAttachmentDao().insert(ServiceAttachmentEntity(
            publicId = identity("$prefix-attachment"), serviceRecordId = record, sequenceInRecord = 0,
            relativePath = staged.relativePath, displayName = staged.displayName, mimeType = staged.mimeType,
            createdAt = 100, updatedAt = 200,
        ))
    }
}
