package com.kumo.bubu.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.ExpenseRecordInput
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import java.time.LocalDate
import java.time.MonthDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineReminderRepositoryTest {
    private lateinit var database: BuBuDatabase
    private lateinit var repository: OfflineReminderRepository
    private val today = LocalDate.of(2026, 8, 16)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BuBuDatabase::class.java,
        ).allowMainThreadQueries().build()
        val settings = FakeStatutoryReminderSettings()
        repository = OfflineReminderRepository(
            database = database,
            reminderDao = database.vehicleReminderDao(),
            vehicleDao = database.vehicleDao(),
            fuelRecordDao = database.fuelRecordDao(),
            serviceRecordDao = database.serviceRecordDao(),
            statutoryReminderSettings = settings,
            labels = AutomaticReminderLabels("License tax", "Road fee", "Inspection", "Inspection 2"),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun refreshCreatesOneCurrentCyclePerApplicableRuleAndUpdatesConfirmedInspectionDate() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())

        repository.refreshAutomaticReminders(today)
        repository.refreshAutomaticReminders(today)
        var reminders = repository.observeReminders().first()

        assertEquals(3, reminders.size)
        assertEquals(
            setOf(ReminderSource.LICENSE_TAX, ReminderSource.ROAD_MAINTENANCE_FEE, ReminderSource.PERIODIC_INSPECTION),
            reminders.mapTo(mutableSetOf()) { it.source },
        )

        val existing = requireNotNull(database.vehicleDao().getById(vehicleId))
        database.vehicleDao().update(existing.copy(primaryInspectionMonthDay = MonthDay.of(12, 5)))
        repository.refreshAutomaticReminders(today)
        reminders = repository.observeReminders().first()

        assertEquals(3, reminders.size)
        assertEquals(
            LocalDate.of(2026, 12, 5).toEpochDay(),
            reminders.single { it.source == ReminderSource.PERIODIC_INSPECTION }.referenceDateEpochDay,
        )
    }

    @Test
    fun refreshStoresForecastOnlyForTheVehicleWithEnoughPositiveMileageHistory() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle(manufactureYear = null, inspectionMonthDay = null))
        repository.createManualReminder(ManualReminderInput(vehicleId, "Oil", 3_000, null))
        database.fuelRecordDao().insert(
            FuelRecordEntity(
                publicId = "fuel-1",
                vehicleId = vehicleId,
                dateEpochDay = today.toEpochDay(),
                timeMinuteOfDay = null,
                sequenceInDay = 0,
                odometerKm = 1_900,
                fuelVolumeMl = 10_000,
                pricePerLiterMilli = null,
                totalCostTwd = 300,
                isFullTank = true,
                fuelProduct = null,
                note = null,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        repository.refreshAutomaticReminders(today)
        val manual = repository.observeReminders().first().single { it.source == ReminderSource.MANUAL }

        assertEquals(LocalDate.of(2026, 11, 14).toEpochDay(), manual.estimatedNotificationEpochDay)
        assertTrue(manual.dueOdometerKm == 3_000L)
    }

    @Test
    fun sameCycleTaxExpenseCanCompleteAndLinkOnlyItsMatchingAutomaticReminder() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle(manufactureYear = null, inspectionMonthDay = null))
        repository.refreshAutomaticReminders(today)
        val before = repository.observeReminders().first()
        val tax = before.single { it.source == ReminderSource.LICENSE_TAX }

        val expenses = OfflineExpenseRepository(
            database,
            database.vehicleDao(),
            database.expenseRecordDao(),
            database.vehicleReminderDao(),
            repository,
        )
        val expenseId = expenses.createExpenseRecord(
            ExpenseRecordInput(
                vehicleId = vehicleId,
                dateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
                timeMinuteOfDay = null,
                category = ExpenseCategory.LICENSE_TAX,
                totalCostTwd = 7_120,
                completeSameCycleReminder = true,
            ),
        )

        assertEquals(tax.id, expenses.getExpenseRecord(expenseId)?.completedReminderId)
        val completedTax = requireNotNull(database.vehicleReminderDao().getById(tax.id))
        assertTrue(completedTax.completedAt != null)
        assertEquals(expenseId, completedTax.completedByExpenseRecordId)
        assertTrue(
            repository.observeReminders().first()
                .single { it.source == ReminderSource.ROAD_MAINTENANCE_FEE }
                .completedAt == null,
        )

        expenses.updateExpenseRecord(
            expenseId,
            ExpenseRecordInput(
                vehicleId = vehicleId,
                dateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
                timeMinuteOfDay = null,
                category = ExpenseCategory.OTHER,
                totalCostTwd = 7_120,
            ),
        )
        assertTrue(expenses.getExpenseRecord(expenseId)?.completedReminderId == null)
        assertTrue(requireNotNull(database.vehicleReminderDao().getById(tax.id)).completedAt == null)
    }

    @Test
    fun historicalTaxExpenseNeverCompletesTheCurrentCycleReminder() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle(manufactureYear = null, inspectionMonthDay = null))
        repository.refreshAutomaticReminders(today)
        val expenses = OfflineExpenseRepository(
            database,
            database.vehicleDao(),
            database.expenseRecordDao(),
            database.vehicleReminderDao(),
            repository,
        )

        val expenseId = expenses.createExpenseRecord(
            ExpenseRecordInput(
                vehicleId = vehicleId,
                dateEpochDay = LocalDate.of(2025, 4, 10).toEpochDay(),
                timeMinuteOfDay = null,
                category = ExpenseCategory.LICENSE_TAX,
                totalCostTwd = 7_120,
                completeSameCycleReminder = true,
            ),
        )

        val expense = requireNotNull(expenses.getExpenseRecord(expenseId))
        val linkedReminderId = requireNotNull(expense.completedReminderId)
        val linkedHistoricalReminder = requireNotNull(database.vehicleReminderDao().getById(linkedReminderId))
        assertTrue(linkedHistoricalReminder.automaticKey?.endsWith(":2025") == true)
        assertTrue(linkedHistoricalReminder.completedAt != null)
        val currentCycleReminder = requireNotNull(
            database.vehicleReminderDao().getByAutomaticKey("statutory:$vehicleId:LICENSE_TAX:2026"),
        )
        assertTrue(currentCycleReminder.completedAt == null)
    }

    @Test
    fun taxExpenseCanEnsureAndCompleteItsCycleBeforeAnyAutomaticRefresh() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle(manufactureYear = null, inspectionMonthDay = null))
        val expenses = OfflineExpenseRepository(
            database,
            database.vehicleDao(),
            database.expenseRecordDao(),
            database.vehicleReminderDao(),
            repository,
        )

        val expenseId = expenses.createExpenseRecord(
            ExpenseRecordInput(
                vehicleId = vehicleId,
                dateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
                timeMinuteOfDay = null,
                category = ExpenseCategory.LICENSE_TAX,
                totalCostTwd = 7_120,
                completeSameCycleReminder = true,
            ),
        )

        val expense = requireNotNull(expenses.getExpenseRecord(expenseId))
        val reminderId = requireNotNull(expense.completedReminderId)
        val reminder = requireNotNull(database.vehicleReminderDao().getById(reminderId))
        assertEquals(expenseId, reminder.completedByExpenseRecordId)
        assertTrue(reminder.completedAt != null)
    }

    @Test
    fun statutoryEnablePreferenceCarriesIntoTheNextCycle() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle(manufactureYear = null, inspectionMonthDay = null))
        repository.refreshAutomaticReminders(today)
        val tax2026 = requireNotNull(
            database.vehicleReminderDao().getByAutomaticKey("statutory:$vehicleId:LICENSE_TAX:2026"),
        )
        repository.setReminderEnabled(tax2026.id, false)

        repository.refreshAutomaticReminders(LocalDate.of(2027, 1, 2))

        val tax2027 = requireNotNull(
            database.vehicleReminderDao().getByAutomaticKey("statutory:$vehicleId:LICENSE_TAX:2027"),
        )
        assertTrue(!tax2027.isEnabled)
    }

    @Test
    fun deletingLinkedTaxExpenseReopensOnlyTheReminderItCompleted() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle(manufactureYear = null, inspectionMonthDay = null))
        repository.refreshAutomaticReminders(today)
        val expenses = OfflineExpenseRepository(
            database,
            database.vehicleDao(),
            database.expenseRecordDao(),
            database.vehicleReminderDao(),
            repository,
        )
        val expenseId = expenses.createExpenseRecord(
            ExpenseRecordInput(
                vehicleId = vehicleId,
                dateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
                timeMinuteOfDay = null,
                category = ExpenseCategory.LICENSE_TAX,
                totalCostTwd = 7_120,
                completeSameCycleReminder = true,
            ),
        )
        val tax = repository.observeReminders().first().single { it.source == ReminderSource.LICENSE_TAX }

        expenses.deleteExpenseRecord(expenseId)

        val reopened = requireNotNull(database.vehicleReminderDao().getById(tax.id))
        assertTrue(reopened.completedAt == null)
        assertTrue(reopened.completedByExpenseRecordId == null)
    }

    private fun vehicle(
        manufactureYear: Int? = 2020,
        inspectionMonthDay: MonthDay? = MonthDay.of(10, 20),
    ) = VehicleEntity(
        publicId = "vehicle-1",
        name = "Car",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = manufactureYear,
        engineDisplacementCc = 1_800,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = LocalDate.of(2026, 5, 18).toEpochDay(),
        trackingStartOdometerKm = 1_000,
        currentOdometerKm = 1_000,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
        primaryInspectionMonthDay = inspectionMonthDay,
    )
}

private class FakeStatutoryReminderSettings : StatutoryReminderSettings {
    private val enabled = MutableStateFlow(true)

    override fun observeTaxAndFeeEnabled(): Flow<Boolean> = enabled

    override suspend fun setTaxAndFeeEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}
