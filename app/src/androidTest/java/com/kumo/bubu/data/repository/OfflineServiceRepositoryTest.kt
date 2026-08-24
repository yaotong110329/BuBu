package com.kumo.bubu.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.ServiceAttachmentInput
import com.kumo.bubu.domain.model.BuiltInServiceTypeSeed
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.ServiceItemInput
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.ServiceRecordType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineServiceRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: BuBuDatabase
    private lateinit var repository: OfflineServiceRepository
    private val managedTestFiles = mutableListOf<File>()
    private val twoDaysMillis = 2L * 24L * 60L * 60L * 1_000L
    private val builtInSeeds = listOf(
        BuiltInServiceTypeSeed("engine-oil", "機油", VehicleType.CAR, true),
        BuiltInServiceTypeSeed("other", "其他", VehicleType.CAR),
        BuiltInServiceTypeSeed("engine-oil", "機油", VehicleType.MOTORCYCLE, true),
        BuiltInServiceTypeSeed("other", "其他", VehicleType.MOTORCYCLE),
    )

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).allowMainThreadQueries().build()
        repository = OfflineServiceRepository(
            database = database,
            vehicleDao = database.vehicleDao(),
            fuelRecordDao = database.fuelRecordDao(),
            serviceRecordDao = database.serviceRecordDao(),
            serviceItemDao = database.serviceItemDao(),
            serviceTypeDao = database.serviceTypeDao(),
            serviceAttachmentDao = database.serviceAttachmentDao(),
            vehicleReminderDao = database.vehicleReminderDao(),
            pendingAttachmentDeletionDao = database.pendingAttachmentDeletionDao(),
            attachmentStore = PrivateAttachmentStore(context, "附件"),
            builtInServiceTypeSeeds = builtInSeeds,
        )
    }

    @After fun tearDown() {
        database.close()
        managedTestFiles.forEach(File::delete)
        managedTestFiles.clear()
    }

    @Test fun createEditAndDeleteWorkOrderKeepsItemsAndOdometerConsistent() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val workOrderId = repository.createServiceRecord(input(vehicleId, 1_500, listOf(item("機油", 500), item("濾芯", 100))))
        val created = requireNotNull(repository.getServiceRecord(workOrderId))
        assertEquals(2, created.items.size)
        assertEquals(1_500L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)

        repository.createServiceRecord(input(vehicleId, 900, listOf(item("補登", 50))))
        assertEquals(1_500L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)

        repository.updateServiceRecord(workOrderId, input(vehicleId, 1_500, listOf(item("機油", 600, created.items.first().id))))
        val updated = requireNotNull(repository.getServiceRecord(workOrderId))
        assertEquals(created.items.first().id, updated.items.single().id)
        assertEquals(1_500L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)

        repository.deleteServiceRecord(workOrderId)
        assertEquals(1, repository.observeRecentServiceRecords().first().size)
        assertEquals(900L, requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm)
    }

    @Test
    fun localizedBuiltInTypeSeedsAreStableAndIdempotent() = runBlocking {
        repository.ensureDefaultServiceTypes()
        repository.ensureDefaultServiceTypes()

        val types = database.serviceTypeDao().getAll()
        assertEquals(
            listOf(
                "builtin-car-engine-oil",
                "builtin-car-other",
                "builtin-motorcycle-engine-oil",
                "builtin-motorcycle-other",
            ),
            types.map { it.publicId },
        )
        assertEquals(listOf("機油", "其他", "機油", "其他"), types.map { it.name })
    }

    @Test
    fun defaultSeedRefreshArchivesObsoleteBuiltInsWithoutDeletingThem() = runBlocking {
        val obsoleteId = database.serviceTypeDao().insert(
            ServiceTypeEntity(
                publicId = "builtin-car-legacy-item",
                name = "舊內建項目",
                vehicleType = VehicleType.CAR,
                isBuiltIn = true,
                isArchived = false,
                sortOrder = 0,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        repository.ensureDefaultServiceTypes()

        val obsolete = requireNotNull(database.serviceTypeDao().getById(obsoleteId))
        assertTrue(obsolete.isArchived)
        assertEquals("舊內建項目", obsolete.name)
    }

    @Test
    fun createUpdateAndDeleteWorkOrderKeepsReminderRowsConsistent() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val workOrderId = repository.createServiceRecord(
            input(
                vehicleId = vehicleId,
                odometer = 1_500,
                items = listOf(
                    item(
                        name = "機油",
                        price = 500,
                        nextDueOdometerKm = 2_500,
                        nextDueDateEpochDay = 21_000,
                    ),
                ),
            ),
        )
        val createdItem = requireNotNull(repository.getServiceRecord(workOrderId)).items.single()
        val createdReminder = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(createdItem.id),
        )
        assertEquals(vehicleId, createdReminder.vehicleId)
        assertEquals("機油", createdReminder.title)
        assertEquals(2_500L, createdReminder.dueOdometerKm)
        assertEquals(21_000L, createdReminder.dueDateEpochDay)

        repository.updateServiceRecord(
            workOrderId,
            input(
                vehicleId = vehicleId,
                odometer = 1_600,
                items = listOf(
                    item(
                        name = "機油與機油芯",
                        price = 650,
                        id = createdItem.id,
                        nextDueOdometerKm = 3_000,
                        nextDueDateEpochDay = 21_500,
                    ),
                ),
            ),
        )
        val updatedReminder = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(createdItem.id),
        )
        assertEquals(createdReminder.id, updatedReminder.id)
        assertEquals(createdReminder.publicId, updatedReminder.publicId)
        assertEquals("機油與機油芯", updatedReminder.title)
        assertEquals(3_000L, updatedReminder.dueOdometerKm)
        assertEquals(21_500L, updatedReminder.dueDateEpochDay)

        repository.updateServiceRecord(
            workOrderId,
            input(
                vehicleId = vehicleId,
                odometer = 1_600,
                items = listOf(item("機油與機油芯", 650, createdItem.id)),
            ),
        )
        assertNull(database.vehicleReminderDao().getBySourceServiceItemId(createdItem.id))

        repository.updateServiceRecord(
            workOrderId,
            input(
                vehicleId = vehicleId,
                odometer = 1_600,
                items = listOf(
                    item(
                        name = "機油與機油芯",
                        price = 650,
                        id = createdItem.id,
                        nextDueOdometerKm = 3_500,
                    ),
                ),
            ),
        )
        assertTrue(database.vehicleReminderDao().getBySourceServiceItemId(createdItem.id) != null)

        repository.deleteServiceRecord(workOrderId)

        assertNull(database.vehicleReminderDao().getBySourceServiceItemId(createdItem.id))
    }

    @Test
    fun laterMatchingWorkOrderCompletesPreviousReminderAndDeleteRestoresIt() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val firstRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                1_500,
                listOf(item("機油", 500, nextDueOdometerKm = 2_500)),
            ),
        )
        val firstItem = requireNotNull(repository.getServiceRecord(firstRecordId)).items.single()

        val secondRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                2_500,
                listOf(item("機油", 600, nextDueOdometerKm = 3_500)),
            ),
        )
        val completedReminder = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(firstItem.id),
        )
        assertEquals(secondRecordId, completedReminder.completedByServiceRecordId)
        assertTrue(completedReminder.completedAt != null)

        repository.deleteServiceRecord(secondRecordId)

        val restoredReminder = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(firstItem.id),
        )
        assertNull(restoredReminder.completedByServiceRecordId)
        assertNull(restoredReminder.completedAt)
    }

    @Test
    fun unrelatedWorkOrderKeepsReminderCompletionTimestampsStable() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val firstRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                1_500,
                listOf(item("機油", 500, nextDueOdometerKm = 2_500)),
            ),
        )
        val firstItem = requireNotNull(repository.getServiceRecord(firstRecordId)).items.single()
        val secondRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                2_500,
                listOf(item("機油", 600, nextDueOdometerKm = 3_500)),
            ),
        )
        val secondItem = requireNotNull(repository.getServiceRecord(secondRecordId)).items.single()
        val completedReminder = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(firstItem.id),
        )
        val activeReminder = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(secondItem.id),
        )
        val stableCompletedAt = 123_456L
        val stableCompletedUpdatedAt = 234_567L
        val stableActiveUpdatedAt = 345_678L
        database.vehicleReminderDao().update(
            completedReminder.copy(
                completedAt = stableCompletedAt,
                updatedAt = stableCompletedUpdatedAt,
            ),
        )
        database.vehicleReminderDao().update(
            activeReminder.copy(updatedAt = stableActiveUpdatedAt),
        )

        repository.createServiceRecord(
            input(
                vehicleId,
                2_600,
                listOf(item("輪胎", 800)),
            ),
        )

        val unchangedCompleted = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(firstItem.id),
        )
        val unchangedActive = requireNotNull(
            database.vehicleReminderDao().getBySourceServiceItemId(secondItem.id),
        )
        assertEquals(secondRecordId, unchangedCompleted.completedByServiceRecordId)
        assertEquals(stableCompletedAt, unchangedCompleted.completedAt)
        assertEquals(stableCompletedUpdatedAt, unchangedCompleted.updatedAt)
        assertNull(unchangedActive.completedByServiceRecordId)
        assertNull(unchangedActive.completedAt)
        assertEquals(stableActiveUpdatedAt, unchangedActive.updatedAt)
    }

    @Test
    fun backfilledWorkOrderRebuildsReminderCompletionTimeline() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val firstRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                1_000,
                listOf(item("機油", 500, nextDueOdometerKm = 2_000)),
                dateEpochDay = 20_000,
            ),
        )
        val lastRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                3_000,
                listOf(item("機油", 700, nextDueOdometerKm = 4_000)),
                dateEpochDay = 20_002,
            ),
        )
        val firstItem = requireNotNull(repository.getServiceRecord(firstRecordId)).items.single()
        val lastItem = requireNotNull(repository.getServiceRecord(lastRecordId)).items.single()
        assertEquals(
            lastRecordId,
            requireNotNull(database.vehicleReminderDao().getBySourceServiceItemId(firstItem.id))
                .completedByServiceRecordId,
        )

        val middleRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                2_000,
                listOf(item("機油", 600, nextDueOdometerKm = 3_000)),
                dateEpochDay = 20_001,
            ),
        )
        val middleItem = requireNotNull(repository.getServiceRecord(middleRecordId)).items.single()

        assertEquals(
            middleRecordId,
            requireNotNull(database.vehicleReminderDao().getBySourceServiceItemId(firstItem.id))
                .completedByServiceRecordId,
        )
        assertEquals(
            lastRecordId,
            requireNotNull(database.vehicleReminderDao().getBySourceServiceItemId(middleItem.id))
                .completedByServiceRecordId,
        )
        assertNull(
            requireNotNull(database.vehicleReminderDao().getBySourceServiceItemId(lastItem.id))
                .completedByServiceRecordId,
        )
    }

    @Test
    fun laterWorkOrderCompletesAllSameTypeRemindersFromOneEarlierWorkOrder() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val firstRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                1_000,
                listOf(
                    item("機油", 500, nextDueOdometerKm = 2_000),
                    item("機油", 300, nextDueDateEpochDay = 21_000),
                ),
                dateEpochDay = 20_000,
            ),
        )
        val firstItems = requireNotNull(repository.getServiceRecord(firstRecordId)).items

        val laterRecordId = repository.createServiceRecord(
            input(
                vehicleId,
                2_000,
                listOf(item("機油", 600)),
                dateEpochDay = 20_001,
            ),
        )

        assertEquals(
            listOf(laterRecordId, laterRecordId),
            firstItems.map { item ->
                requireNotNull(database.vehicleReminderDao().getBySourceServiceItemId(item.id))
                    .completedByServiceRecordId
            },
        )
    }

    @Test
    fun reminderFailureRollsBackWorkOrderItemsAndOdometer() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_service_reminder_insert
            BEFORE INSERT ON vehicle_reminders
            BEGIN
                SELECT RAISE(ABORT, 'forced reminder failure');
            END
            """.trimIndent(),
        )
        try {
            val result = runCatching {
                repository.createServiceRecord(
                    input(
                        vehicleId = vehicleId,
                        odometer = 1_500,
                        items = listOf(
                            item(
                                name = "機油",
                                price = 500,
                                nextDueOdometerKm = 2_500,
                            ),
                        ),
                    ),
                )
            }

            assertTrue(result.isFailure)
            assertTrue(repository.observeRecentServiceRecords().first().isEmpty())
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM service_items"))
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM vehicle_reminders"))
            assertEquals(
                100L,
                requireNotNull(database.vehicleDao().getById(vehicleId)).currentOdometerKm,
            )
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_service_reminder_insert")
        }
    }

    @Test
    fun deletingWorkOrderCascadesAttachmentRelationAndDeletesPrivateCopy() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val (attachment, privateCopy) = managedAttachment()
        val workOrderId = repository.createServiceRecord(
            input(
                vehicleId = vehicleId,
                odometer = 1_500,
                items = listOf(item("機油", 500)),
                attachments = listOf(attachment),
            ),
        )
        val savedAttachment = requireNotNull(repository.getServiceRecord(workOrderId))
            .attachments
            .single()
        assertEquals(attachment.relativePath, savedAttachment.relativePath)
        assertEquals(1, database.serviceAttachmentDao().getForRecord(workOrderId).size)
        assertTrue(privateCopy.isFile)

        repository.deleteServiceRecord(workOrderId)

        assertTrue(database.serviceAttachmentDao().getForRecord(workOrderId).isEmpty())
        assertFalse(privateCopy.exists())
        assertTrue(database.pendingAttachmentDeletionDao().getAll().isEmpty())
    }

    @Test
    fun privateAttachmentReadsStayBehindRepositoryPathValidation() = runBlocking {
        val (attachment, privateCopy) = managedAttachment()

        assertArrayEquals(
            privateCopy.readBytes(),
            repository.readServiceAttachmentBytes(attachment.relativePath),
        )
        assertTrue(
            runCatching {
                repository.readServiceAttachmentBytes("../outside-private-storage.png")
            }.isFailure,
        )
    }

    @Test
    fun startupReconciliationKeepsFreshDraftButDeletesStaleOrphan() = runBlocking {
        val (_, privateCopy) = managedAttachment()

        repository.retryPendingAttachmentDeletions()
        assertTrue(privateCopy.isFile)

        assertTrue(privateCopy.setLastModified(System.currentTimeMillis() - twoDaysMillis))
        repository.retryPendingAttachmentDeletions()

        assertFalse(privateCopy.exists())
        assertTrue(database.pendingAttachmentDeletionDao().getAll().isEmpty())
    }

    @Test
    fun updateRejectsDuplicateAndForeignItemIdsWithoutChangingEitherWorkOrder() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val firstId = repository.createServiceRecord(
            input(vehicleId, 1_500, listOf(item("機油", 500))),
        )
        val secondId = repository.createServiceRecord(
            input(vehicleId, 1_600, listOf(item("機油芯", 200))),
        )
        val firstItem = requireNotNull(repository.getServiceRecord(firstId)).items.single()
        val secondItem = requireNotNull(repository.getServiceRecord(secondId)).items.single()

        assertTrue(
            runCatching {
                repository.updateServiceRecord(
                    firstId,
                    input(
                        vehicleId,
                        1_500,
                        listOf(
                            item("機油", 500, firstItem.id),
                            item("機油", 500, firstItem.id),
                        ),
                    ),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                repository.updateServiceRecord(
                    firstId,
                    input(vehicleId, 1_500, listOf(item("機油芯", 200, secondItem.id))),
                )
            }.isFailure,
        )

        assertEquals(1, requireNotNull(repository.getServiceRecord(firstId)).items.size)
        assertEquals(1, requireNotNull(repository.getServiceRecord(secondId)).items.size)
    }

    private fun vehicle() = VehicleEntity(publicId = "service-vehicle", name = "測試車", vehicleType = VehicleType.CAR, motorcycleClass = null, brand = null, model = null, manufactureYear = null, engineDisplacementCc = null, licensePlate = null, powertrainType = null, trackingStartDateEpochDay = 19_000, trackingStartOdometerKm = 100, currentOdometerKm = 100, note = null, isArchived = false, createdAt = 1, updatedAt = 1)
    private fun item(
        name: String,
        price: Long,
        id: Long? = null,
        nextDueOdometerKm: Long? = null,
        nextDueDateEpochDay: Long? = null,
    ) = ServiceItemInput(
        id = id,
        nameSnapshot = name,
        quantityMilli = 1_000,
        unitPriceTwd = price,
        nextDueOdometerKm = nextDueOdometerKm,
        nextDueDateEpochDay = nextDueDateEpochDay,
    )

    private fun input(
        vehicleId: Long,
        odometer: Long,
        items: List<ServiceItemInput>,
        attachments: List<ServiceAttachmentInput> = emptyList(),
        dateEpochDay: Long = 20_000,
    ) = ServiceRecordInput(
        vehicleId = vehicleId,
        dateEpochDay = dateEpochDay,
        timeMinuteOfDay = null,
        odometerKm = odometer,
        recordType = ServiceRecordType.MAINTENANCE,
        title = "定期保養",
        paymentMethod = null,
        totalCostTwd = items.sumOf { it.subtotalTwd },
        items = items,
        attachments = attachments,
    )

    private fun managedAttachment(): Pair<ServiceAttachmentInput, File> {
        val relativePath = "attachments/service/repository-test-${UUID.randomUUID()}.png"
        val file = File(context.filesDir, relativePath)
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        file.writeBytes(byteArrayOf(0x42, 0x75, 0x42, 0x75))
        managedTestFiles += file
        return ServiceAttachmentInput(
            relativePath = relativePath,
            displayName = "收據.png",
            mimeType = "image/png",
            isStaged = true,
        ) to file
    }

    private fun scalarInt(query: String): Int = database.openHelper.readableDatabase
        .query(query)
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
