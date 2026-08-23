package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.backup.BackupArchiveWriter
import com.kumo.bubu.data.backup.BackupData
import com.kumo.bubu.data.backup.BackupFileContent
import com.kumo.bubu.data.backup.BackupVehicle
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.VehicleType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineRestoreRepositoryTest {
    private lateinit var context: Context
    private lateinit var targetDatabase: BuBuDatabase
    private lateinit var sourceDatabase: BuBuDatabase
    private lateinit var candidate: File
    private lateinit var recoveryDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        targetDatabase = database()
        sourceDatabase = database()
        candidate = File(context.cacheDir, "restore-candidate-${UUID.randomUUID()}.bubu")
        recoveryDirectory = File(context.cacheDir, "restore-recovery-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        targetDatabase.close()
        sourceDatabase.close()
        candidate.delete()
        recoveryDirectory.deleteRecursively()
    }

    @Test
    fun replacesAllCurrentDataOnlyAfterCreatingRecoveryBackup() = runBlocking {
        sourceDatabase.vehicleDao().insert(vehicle("imported", "匯入車"))
        val sourceBackup = backupRepository(sourceDatabase)
        sourceBackup.writePrivateBackup(candidate)
        targetDatabase.vehicleDao().insert(vehicle("original", "原有車"))
        val restoreRepository = OfflineRestoreRepository(
            context = context,
            database = targetDatabase,
            attachmentStore = attachmentStore(),
            backupRepository = backupRepository(targetDatabase),
            recoveryDirectory = recoveryDirectory,
        )

        val preview = restoreRepository.preview(Uri.fromFile(candidate).toString())
        restoreRepository.restore(Uri.fromFile(candidate).toString())

        assertEquals(1, preview.vehicleCount)
        assertEquals(listOf("匯入車"), targetDatabase.vehicleDao().getAll().map(VehicleEntity::name))
        assertEquals(1, recoveryDirectory.listFiles().orEmpty().count { it.extension == "bubu" })
        assertTrue(recoveryDirectory.listFiles().orEmpty().single().length() > 0)
    }

    @Test
    fun restoresPreviousDataWhenVerifiedArchiveFailsDuringMigration() = runBlocking {
        writeArchiveThatFailsDuringEntityMigration(candidate)
        targetDatabase.vehicleDao().insert(vehicle("original", "原有車"))
        val restoreRepository = OfflineRestoreRepository(
            context = context,
            database = targetDatabase,
            attachmentStore = attachmentStore(),
            backupRepository = backupRepository(targetDatabase),
            recoveryDirectory = recoveryDirectory,
        )

        val result = runCatching { restoreRepository.restore(Uri.fromFile(candidate).toString()) }

        assertTrue(result.isFailure)
        assertEquals(listOf("原有車"), targetDatabase.vehicleDao().getAll().map(VehicleEntity::name))
        assertEquals(1, recoveryDirectory.listFiles().orEmpty().count { it.extension == "bubu" })
    }

    @Test
    fun restoresIntoDiskBackedDatabaseWithoutBlocking() = runBlocking {
        val databaseName = "restore-disk-${UUID.randomUUID()}.db"
        val diskDatabase = database(databaseName)
        try {
            sourceDatabase.vehicleDao().insert(vehicle("imported", "匯入車"))
            backupRepository(sourceDatabase).writePrivateBackup(candidate)
            diskDatabase.vehicleDao().insert(vehicle("original", "原有車"))
            val restoreRepository = OfflineRestoreRepository(
                context = context,
                database = diskDatabase,
                attachmentStore = attachmentStore(),
                backupRepository = backupRepository(diskDatabase),
                recoveryDirectory = recoveryDirectory,
            )

            withTimeout(5_000) {
                restoreRepository.restore(Uri.fromFile(candidate).toString())
            }

            assertEquals(listOf("匯入車"), diskDatabase.vehicleDao().getAll().map(VehicleEntity::name))
        } finally {
            diskDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun writeArchiveThatFailsDuringEntityMigration(destination: File) {
        val data = BackupData(
            vehicles = listOf(
                BackupVehicle(
                    publicId = "invalid-import",
                    name = "無效匯入車",
                    vehicleType = "NOT_A_VEHICLE_TYPE",
                    motorcycleClass = null,
                    brand = null,
                    model = null,
                    manufactureYear = null,
                    engineDisplacementCc = null,
                    licensePlate = null,
                    powertrainType = null,
                    trackingStartDateEpochDay = 20_000,
                    trackingStartOdometerKm = 0,
                    currentOdometerKm = 0,
                    note = null,
                    isArchived = false,
                    createdAt = 1,
                    updatedAt = 1,
                    primaryInspectionMonthDay = null,
                    secondaryInspectionMonthDay = null,
                ),
            ),
            fuelRecords = emptyList(),
            serviceTypes = emptyList(),
            serviceRecords = emptyList(),
            serviceItems = emptyList(),
            expenseRecords = emptyList(),
            reminders = emptyList(),
            attachments = emptyList(),
        )
        BackupArchiveWriter.write(
            destination = destination,
            appVersion = "test",
            createdAtEpochMillis = 1,
            recordCounts = mapOf(
                "vehicles" to 1,
                "fuelRecords" to 0,
                "serviceTypes" to 0,
                "serviceRecords" to 0,
                "serviceItems" to 0,
                "expenseRecords" to 0,
                "reminders" to 0,
                "attachments" to 0,
            ),
            contents = listOf(
                BackupFileContent(
                    relativePath = "data.json",
                    bytes = Json.encodeToString(BackupData.serializer(), data).encodeToByteArray(),
                ),
            ),
        )
    }

    private fun database(name: String? = null) = name?.let {
        Room.databaseBuilder(context, BuBuDatabase::class.java, it)
            .allowMainThreadQueries()
            .build()
    } ?: Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun attachmentStore() = PrivateAttachmentStore(context, "附件")

    private fun backupRepository(database: BuBuDatabase) = OfflineBackupRepository(
        context = context,
        database = database,
        attachmentStore = attachmentStore(),
    )

    private fun vehicle(publicId: String, name: String) = VehicleEntity(
        publicId = publicId,
        name = name,
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 0,
        currentOdometerKm = 0,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )
}
