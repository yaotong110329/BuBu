package com.kumo.bubu.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.R
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.repository.DataStoreMaintenanceSummaryStateStore
import com.kumo.bubu.data.repository.OfflineServiceRepository
import com.kumo.bubu.domain.model.BuiltInServiceTypeSeed
import com.kumo.bubu.domain.model.MaintenanceReminderStatus
import com.kumo.bubu.domain.model.ServiceItemInput
import com.kumo.bubu.domain.model.ServiceRecordInput
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.ServiceReminderPreferenceInput
import com.kumo.bubu.domain.model.VehicleType
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaintenanceSummaryNotificationIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: BuBuDatabase
    private lateinit var repository: OfflineServiceRepository
    private lateinit var manager: NotificationManager
    private lateinit var scope: CoroutineScope
    private lateinit var preferencesFile: File
    private lateinit var stateStore: DataStoreMaintenanceSummaryStateStore
    private val today = LocalDate.of(2026, 10, 2)

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ReminderNotifications.CHANNEL_ID, "Test reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.cancel(ReminderNotifications.MAINTENANCE_NOTIFICATION_ID)
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).build()
        repository = OfflineServiceRepository(
            database = database,
            vehicleDao = database.vehicleDao(),
            fuelRecordDao = database.fuelRecordDao(),
            serviceRecordDao = database.serviceRecordDao(),
            serviceItemDao = database.serviceItemDao(),
            serviceTypeDao = database.serviceTypeDao(),
            attachmentStore = PrivateAttachmentStore(context, "attachment"),
            builtInServiceTypeSeeds = listOf(BuiltInServiceTypeSeed("engine-oil", "Oil", VehicleType.CAR, true)),
        )
        repository.ensureDefaultServiceTypes()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferencesFile = File(context.filesDir, "maintenance-summary-${UUID.randomUUID()}.preferences_pb")
        stateStore = DataStoreMaintenanceSummaryStateStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }),
        )
    }

    @After fun tearDown() {
        manager.cancel(ReminderNotifications.MAINTENANCE_NOTIFICATION_ID)
        database.close()
        scope.cancel()
        preferencesFile.delete()
    }

    @Test fun serviceWritesArchiveAndRuleDisableUpdateThePostedSummary() = runBlocking {
        val firstVehicle = database.vehicleDao().insert(vehicle("first"))
        val secondVehicle = database.vehicleDao().insert(vehicle("second"))
        val serviceTypeId = database.serviceTypeDao().getAll().single().id
        val gateway = AndroidMaintenanceSummaryNotificationGateway(context)
        val coordinator = coordinator(gateway)

        coordinator.reconcile(allowAlert = true)
        awaitPostedCount(2)
        assertNotNull(stateStore.read().alertedSignature)

        val recordId = repository.createServiceRecord(workOrder(firstVehicle, serviceTypeId))
        val afterCreate = repository.observeMaintenanceReminders(today).first().single { it.vehicleId == firstVehicle }
        assertEquals(10_000L, afterCreate.baseline.lastServiceMileage)
        assertEquals(MaintenanceReminderStatus.NORMAL, afterCreate.calculation.status)
        coordinator.reconcile(allowAlert = false)
        awaitPostedCount(1)

        repository.updateServiceRecord(recordId, workOrder(firstVehicle, null))
        coordinator.reconcile(allowAlert = false)
        awaitPostedCount(2)

        repository.deleteServiceRecord(recordId)
        coordinator.reconcile(allowAlert = false)
        awaitPostedCount(2)

        val archivedVehicle = requireNotNull(database.vehicleDao().getById(secondVehicle))
        database.vehicleDao().update(archivedVehicle.copy(isArchived = true))
        coordinator.reconcile(allowAlert = false)
        awaitPostedCount(1)

        repository.saveServiceReminderPreference(
            ServiceReminderPreferenceInput(
                vehicleId = firstVehicle,
                serviceTypeId = serviceTypeId,
                isEnabled = false,
                intervalKm = 10_000,
                sortOrder = 0,
            ),
        )
        coordinator.reconcile(allowAlert = false)
        withTimeout(10_000) {
            while (gateway.isActive()) delay(50)
        }
        assertFalse(gateway.isActive())
        assertNull(stateStore.read().alertedSignature)
    }

    @Test fun persistentSignatureSurvivesCoordinatorReconstruction() = runBlocking {
        database.vehicleDao().insert(vehicle("one"))
        val gateway = AndroidMaintenanceSummaryNotificationGateway(context)
        coordinator(gateway).reconcile(allowAlert = true)
        val saved = stateStore.read()
        assertNotNull(saved.alertedSignature)
        assertEquals(saved.alertedSignature, saved.renderedSignature)

        scope.cancel()
        scope.coroutineContext[Job]?.join()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        stateStore = DataStoreMaintenanceSummaryStateStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }),
        )
        assertEquals(saved, stateStore.read())
        coordinator(gateway).reconcile(allowAlert = true)
        awaitPostedCount(1)
        assertEquals(saved, stateStore.read())
    }

    private fun coordinator(gateway: MaintenanceSummaryNotificationGateway) = MaintenanceSummaryCoordinator(
        loadReminders = { repository.observeMaintenanceReminders(today).first() },
        notificationsEnabled = { true },
        canPost = { ReminderNotifications.canPost(context) },
        stateStore = stateStore,
        gateway = gateway,
    )

    private suspend fun awaitPostedCount(count: Int) {
        val expected = context.getString(R.string.maintenance_notification_summary, count)
        withTimeout(10_000) {
            while (postedText() != expected) delay(50)
        }
        assertEquals(expected, postedText())
    }

    private fun postedText(): String? {
        val notification = manager.activeNotifications.singleOrNull {
            it.id == ReminderNotifications.MAINTENANCE_NOTIFICATION_ID
        }?.notification ?: return null
        return notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    private fun vehicle(id: String) = VehicleEntity(
        publicId = id, name = id, vehicleType = VehicleType.CAR, motorcycleClass = null,
        brand = null, model = null, manufactureYear = null, engineDisplacementCc = null,
        licensePlate = null, powertrainType = null, trackingStartDateEpochDay = today.toEpochDay(),
        trackingStartOdometerKm = 10_000, currentOdometerKm = 10_000, note = null,
        isArchived = false, useNewVehicleMaintenanceBaseline = true, createdAt = 1, updatedAt = 1,
    )

    private fun workOrder(vehicleId: Long, serviceTypeId: Long?) = ServiceRecordInput(
        vehicleId = vehicleId, dateEpochDay = today.toEpochDay(), timeMinuteOfDay = null,
        odometerKm = 10_000, recordType = ServiceRecordType.MAINTENANCE, title = "Oil",
        paymentMethod = null, totalCostTwd = 100,
        items = listOf(ServiceItemInput("Oil", serviceTypeId, 1_000, unitPriceTwd = 100)),
    )
}
