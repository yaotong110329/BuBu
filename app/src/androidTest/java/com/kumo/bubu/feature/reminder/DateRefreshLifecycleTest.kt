package com.kumo.bubu.feature.reminder

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kumo.bubu.R
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.core.notification.ReminderNotificationScheduler
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.VehicleServiceReminderPreferenceEntity
import com.kumo.bubu.data.repository.AutomaticReminderLabels
import com.kumo.bubu.data.repository.DataStoreReminderNotificationSettings
import com.kumo.bubu.data.repository.OfflineFuelRepository
import com.kumo.bubu.data.repository.OfflineReminderRepository
import com.kumo.bubu.data.repository.OfflineServiceRepository
import com.kumo.bubu.data.repository.OfflineVehicleRepository
import com.kumo.bubu.domain.model.MaintenanceReminderStatus
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import com.kumo.bubu.feature.dashboard.DashboardRoute
import com.kumo.bubu.feature.dashboard.DashboardViewModel
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DateRefreshLifecycleTest {
    @get:Rule val composeRule = createComposeRule(UnconfinedTestDispatcher())
    private lateinit var database: BuBuDatabase
    private lateinit var preferenceScope: CoroutineScope
    private lateinit var preferenceFile: File
    private lateinit var vehicles: OfflineVehicleRepository
    private lateinit var fuel: OfflineFuelRepository
    private lateinit var reminders: OfflineReminderRepository
    private lateinit var services: OfflineServiceRepository
    private lateinit var notificationSettings: DataStoreReminderNotificationSettings
    private val viewModelScopes = mutableListOf<CoroutineScope>()
    private var today = LocalDate.of(2026, 8, 15)
    private var owner: TestLifecycleOwner? = null

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java).allowMainThreadQueries().build()
        preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferenceFile = File(context.cacheDir, "ticket07-${UUID.randomUUID()}.preferences_pb")
        val preferences = PreferenceDataStoreFactory.create(scope = preferenceScope, produceFile = { preferenceFile })
        vehicles = OfflineVehicleRepository(database.vehicleDao(), database.fuelRecordDao(), database.serviceRecordDao(), preferences)
        fuel = OfflineFuelRepository(database, database.vehicleDao(), database.fuelRecordDao(), database.serviceRecordDao(), preferences)
        services = OfflineServiceRepository(database, database.vehicleDao(), database.fuelRecordDao(),
            database.serviceRecordDao(), database.serviceItemDao(), database.serviceTypeDao())
        reminders = OfflineReminderRepository(database, database.vehicleReminderDao(), database.vehicleDao(),
            database.fuelRecordDao(), database.serviceRecordDao(), object : StatutoryReminderSettings {
                override fun observeTaxAndFeeEnabled(): Flow<Boolean> = MutableStateFlow(false)
                override suspend fun setTaxAndFeeEnabled(enabled: Boolean) = Unit
            }, AutomaticReminderLabels("稅", "費", "檢驗", "第二次檢驗"))
        notificationSettings = DataStoreReminderNotificationSettings(preferences)
        val vehicleId = database.vehicleDao().insert(VehicleEntity(publicId = UUID.randomUUID().toString(),
            name = "日期測試車", vehicleType = VehicleType.CAR, motorcycleClass = null,
            brand = null, model = null, manufactureYear = null, engineDisplacementCc = null,
            licensePlate = null, powertrainType = null, trackingStartDateEpochDay = 19_000,
            trackingStartOdometerKm = 1, currentOdometerKm = 9_000, note = null,
            isArchived = false, createdAt = 1, updatedAt = 1))
        val recordId = database.serviceRecordDao().insert(ServiceRecordEntity(publicId = UUID.randomUUID().toString(),
            vehicleId = vehicleId, dateEpochDay = LocalDate.of(2026, 2, 16).toEpochDay(), timeMinuteOfDay = null,
            sequenceInDay = 0, odometerKm = 8_000, recordType = ServiceRecordType.MAINTENANCE,
            title = "基準保養", paymentMethod = null, totalCostTwd = 2_000, note = null, createdAt = 1, updatedAt = 1))
        listOf("日期保養", "混合保養").forEachIndexed { index, name ->
            val typeId = database.serviceTypeDao().insert(ServiceTypeEntity(publicId = UUID.randomUUID().toString(),
                name = name, isBuiltIn = false, isArchived = false, sortOrder = index, createdAt = 1, updatedAt = 1))
            database.serviceItemDao().insertAll(listOf(ServiceItemEntity(publicId = UUID.randomUUID().toString(),
                serviceRecordId = recordId, serviceTypeId = typeId, sequenceInRecord = index,
                nameSnapshot = name, quantityMilli = 1_000, quantityUnit = ServiceQuantityUnit.PIECE,
                unitPriceTwd = 1_000, subtotalTwd = 1_000, nextDueOdometerKm = null,
                nextDueDateEpochDay = null, note = null, createdAt = 1, updatedAt = 1)))
            database.vehicleServiceReminderPreferenceDao().insert(VehicleServiceReminderPreferenceEntity(
                publicId = UUID.randomUUID().toString(), vehicleId = vehicleId, serviceTypeId = typeId,
                intervalKm = if (index == 0) null else 10_000, intervalMonths = 6,
                sortOrder = index, createdAt = 1, updatedAt = 1))
        }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        owner?.let { composeRule.runOnIdle { it.registry.currentState = Lifecycle.State.DESTROYED } }
        viewModelScopes.forEach { it.cancel() }
        viewModelScopes.forEach { it.coroutineContext[Job]?.join() }
        preferenceScope.cancel()
        preferenceScope.coroutineContext[Job]?.join()
        database.close()
        preferenceFile.delete()
    }

    @Test
    fun dashboardResumeUsesNewDateWithTheSameViewModelAndUnchangedRoomRecords() = runBlocking {
        val viewModel = withContext(Dispatchers.Main.immediate) {
            DashboardViewModel(vehicles, fuel, reminders, services, todayProvider = { today })
        }
        viewModelScopes += viewModel.viewModelScope
        val lifecycleOwner = TestLifecycleOwner().also { owner = it }
        composeRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                BuBuTheme { DashboardRoute(viewModel, {}, {}, {}, {}) }
            }
        }
        composeRule.waitUntil(10_000) { viewModel.uiState.value.vehicles.singleOrNull()?.maintenanceUpcomingCount == 2 }
        composeRule.waitUntilAtLeastOneExists(hasText("保養提醒：0 項需要處理 · 2 項即將到期"), 10_000)
        composeRule.onNodeWithText("保養提醒：0 項需要處理 · 2 項即將到期").assertIsDisplayed()
        val before = services.observeRecentServiceRecords().first()
        // Keep collection alive through a brief pause; no Room write or new VM.
        composeRule.runOnIdle {
            lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
            today = LocalDate.of(2026, 8, 16)
            lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitUntil(5_000) { viewModel.uiState.value.vehicles.singleOrNull()?.maintenanceDueCount == 2 }
        composeRule.onNodeWithText("保養提醒：2 項需要處理 · 0 項即將到期").assertIsDisplayed()
        assertEquals(before, services.observeRecentServiceRecords().first())
        assertEquals(listOf(MaintenanceReminderStatus.DUE, MaintenanceReminderStatus.DUE),
            services.observeMaintenanceReminders(today).first().map { it.calculation.status })
    }

    @Test
    fun remindersResumeUsesNewDateWithTheSameViewModelAndUnchangedRoomRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val upcomingSummary = context.getString(R.string.maintenance_reminder_summary_upcoming, 2)
        val dueSummary = context.getString(R.string.maintenance_reminder_summary_due, 2)
        val scheduler = object : ReminderNotificationScheduler {
            override fun scheduleDailyCheck() = Unit
            override fun cancelDailyCheck() = Unit
            override fun cancelReminderNotification(reminderId: Long) = Unit
            override fun cancelAllReminderNotifications() = Unit
        }
        val viewModel = withContext(Dispatchers.Main.immediate) {
            RemindersViewModel(reminders, services, vehicles, notificationSettings, scheduler, todayProvider = { today })
        }
        viewModelScopes += viewModel.viewModelScope
        val lifecycleOwner = TestLifecycleOwner().also { owner = it }
        composeRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                BuBuTheme { RemindersRoute(viewModel) }
            }
        }
        composeRule.waitUntil(10_000) {
            viewModel.uiState.value.maintenanceRows.size == 2 &&
                viewModel.uiState.value.maintenanceRows.all { it.reminder.calculation.status == MaintenanceReminderStatus.UPCOMING }
        }
        composeRule.waitUntilAtLeastOneExists(hasTestTag("reminders-scroll-list"), 10_000)
        composeRule.onNodeWithTag("reminders-scroll-list").performScrollToNode(hasText(upcomingSummary))
        composeRule.onNodeWithText(upcomingSummary).assertIsDisplayed()
        val before = services.observeRecentServiceRecords().first()
        composeRule.runOnIdle {
            lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
            today = LocalDate.of(2026, 8, 16)
            lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitUntil(5_000) {
            viewModel.uiState.value.maintenanceRows.size == 2 &&
                viewModel.uiState.value.maintenanceRows.all { it.reminder.calculation.status == MaintenanceReminderStatus.DUE }
        }
        composeRule.onNodeWithTag("reminders-scroll-list").performScrollToNode(hasText(dueSummary))
        composeRule.onNodeWithText(dueSummary).assertIsDisplayed()
        assertEquals(before, services.observeRecentServiceRecords().first())
        assertEquals(listOf(MaintenanceReminderStatus.DUE, MaintenanceReminderStatus.DUE),
            services.observeMaintenanceReminders(today).first().map { it.calculation.status })
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
