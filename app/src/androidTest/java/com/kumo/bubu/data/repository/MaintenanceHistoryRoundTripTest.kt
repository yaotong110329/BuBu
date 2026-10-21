package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.data.backup.BackupArchiveReader
import com.kumo.bubu.domain.model.*
import com.kumo.bubu.data.repository.MaintenanceHistoryFixture.Companion.CAR_OIL
import com.kumo.bubu.data.repository.MaintenanceHistoryFixture.Companion.MOTORCYCLE_OIL
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaintenanceHistoryRoundTripTest {
    private lateinit var fixture: MaintenanceHistoryFixture

    @Before
    fun setUp() = runBlocking {
        fixture = MaintenanceHistoryFixture(ApplicationProvider.getApplicationContext<Context>())
        fixture.storage.rememberExistingFiles()
    }

    @After
    fun tearDown() = runBlocking {
        fixture.close()
    }

    @Test
    fun formalRoundTripPreservesHistoryPoliciesAndDerivedMaintenanceStates() = runBlocking {
        val history = fixture.createHistory()
        // A real pre-existing target vehicle makes this an overwrite, not an empty import.
        fixture.targetVehicles.createVehicle(fixture.vehicleInput("discarded target", VehicleType.CAR))
        val before = fixture.storage.export(fixture.storage.source, "maintenance-before.bubu")
        val data = BackupArchiveReader.read(before).data
        assertEquals(3, data.vehicles.size)
        assertEquals(1, data.vehicles.count { it.isArchived })
        assertEquals(5, data.serviceRecords.size)
        assertEquals(5, data.serviceReminderPreferences.size)
        assertEquals(1, data.attachments.size)
        val original = derivedStates(fixture.sourceVehicles, fixture.sourceServices)
        assertInitialStates(history, original)
        assertCompletionChain(before, initialCompletionChain)

        fixture.storage.repository().restore(Uri.fromFile(before).toString())

        val after = fixture.storage.export(fixture.storage.target, "maintenance-after.bubu")
        assertBackupArchiveEquals(before, after)
        assertCompletionChain(after, initialCompletionChain)
        val restored = derivedStates(fixture.targetVehicles, fixture.targetServices)
        assertEquals(original, restored)
        assertInitialStates(history, restored)
        assertEquals(setOf(history.car.publicId, history.motorcycle.publicId, history.archivedVehicle.publicId),
            fixture.targetVehicles.observeVehicles().first().map { it.publicId }.toSet())
        assertTrue(fixture.targetVehicles.observeVehicles().first().all { it.currentOdometerKm == 5000L })
    }

    @Test
    fun restoredHistoryRebuildsCompletionChainAfterBackfillRescheduleAndDeletion() = runBlocking {
        val history = fixture.createHistory()
        val before = fixture.storage.export(fixture.storage.source, "mutation-source.bubu")
        val originalData = BackupArchiveReader.read(before).data
        val originalStates = derivedStates(fixture.sourceVehicles, fixture.sourceServices)
        val originalBytes = ZipFile(before).use { zip -> originalData.attachments.associate { attachment ->
            attachment.publicId to zip.getInputStream(zip.getEntry(attachment.archivePath)).use { it.readBytes() }
        } }
        fixture.storage.repository().restore(Uri.fromFile(before).toString())
        val carId = requireNotNull(fixture.targetVehicles.findVehicleIdByPublicId(history.car.publicId))
        val oil = fixture.targetServices.observeServiceTypes().first().single { it.publicId == CAR_OIL }
        val initialRecords = fixture.targetServices.observeServiceRecordDetails(carId).first()
        val last = initialRecords.single { it.record.title == "car-oil-last" }
        val key = history.car.publicId to CAR_OIL

        suspend fun assertState(label: String, chain: Map<String, String?>,
            baseline: MaintenanceBaseline, calculation: MaintenanceReminderCalculation) {
            val archive = fixture.storage.export(fixture.storage.target, "$label.bubu")
            assertCompletionChain(archive, chain)
            val data = BackupArchiveReader.read(archive).data
            assertEquals(originalData.serviceReminderPreferences.sortedBy { it.publicId },
                data.serviceReminderPreferences.sortedBy { it.publicId })
            assertEquals(originalData.attachments, data.attachments)
            ZipFile(archive).use { zip -> data.attachments.forEach { attachment ->
                assertArrayEquals(originalBytes.getValue(attachment.publicId),
                    zip.getInputStream(zip.getEntry(attachment.archivePath)).use { it.readBytes() })
            } }
            val states = derivedStates(fixture.targetVehicles, fixture.targetServices)
            assertEquals(originalStates - key, states - key)
            val current = states.getValue(key)
            assertEquals(baseline, current.baseline)
            assertEquals(calculation, current.calculation)
            assertFalse(current.usesConfiguredMileageBaseline)
            assertEquals(1000L, current.intervalMileage)
            assertEquals(2L, current.intervalMonths)
            assertEquals(MaintenanceReminderType.INSPECT, current.reminderType)
            assertEquals(9, current.sortOrder)
            assertEquals(5000L, requireNotNull(fixture.targetVehicles.getVehicle(carId)).currentOdometerKm)
        }

        val dueBaseline = MaintenanceBaseline(4000, LocalDate.parse("2025-01-01"))
        val due = MaintenanceReminderCalculation(MaintenanceReminderStatus.DUE, 5000,
            LocalDate.parse("2025-03-01"), 0, 45)
        assertState("restored", initialCompletionChain, dueBaseline, due)

        val middleId = fixture.targetServices.createServiceRecord(
            fixture.workOrder(carId, oil, "car-oil-middle", "2024-12-15", 2000))
        val middle = requireNotNull(fixture.targetServices.getServiceRecord(middleId))
        assertState("backfilled", initialCompletionChain + mapOf("car-oil-first" to "car-oil-middle",
            "car-oil-middle" to "car-oil-last"), dueBaseline, due)

        val rescheduled = fixture.workOrder(carId, oil, "car-oil-middle", "2025-01-10", 4500)
        fixture.targetServices.updateServiceRecord(middleId,
            rescheduled.copy(items = listOf(rescheduled.items.single().copy(id = middle.items.single().id))))
        val edited = requireNotNull(fixture.targetServices.getServiceRecord(middleId))
        assertEquals(middle.record.publicId, edited.record.publicId)
        assertEquals(middle.items.single().publicId, edited.items.single().publicId)
        assertState("rescheduled", initialCompletionChain + mapOf("car-oil-last" to "car-oil-middle",
            "car-oil-middle" to null), MaintenanceBaseline(4500, LocalDate.parse("2025-01-10")),
            MaintenanceReminderCalculation(MaintenanceReminderStatus.NORMAL, 5500,
                LocalDate.parse("2025-03-10"), 500, 54))

        fixture.targetServices.deleteServiceRecord(middleId)
        assertNull(fixture.targetServices.getServiceRecord(middleId))
        assertState("deleted-middle", initialCompletionChain, dueBaseline, due)

        fixture.targetServices.deleteServiceRecord(last.record.id)
        assertNull(fixture.targetServices.getServiceRecord(last.record.id))
        assertState("deleted-last", initialCompletionChain - "car-oil-last" + ("car-oil-first" to null),
            MaintenanceBaseline(1000, LocalDate.parse("2024-12-01")),
            MaintenanceReminderCalculation(MaintenanceReminderStatus.OVERDUE, 2000,
                LocalDate.parse("2025-02-01"), -3000, 17))
    }

    private suspend fun derivedStates(vehicles: OfflineVehicleRepository, services: OfflineServiceRepository)
        : Map<Pair<String, String>, DerivedMaintenanceReminder> {
        val vehicleById = vehicles.observeVehicles().first().associateBy { it.id }
        val typeById = services.observeServiceTypes().first().associateBy { it.id }
        val reminders = services.observeMaintenanceReminders(fixture.today).first()
        reminders.forEach { reminder ->
            assertEquals(vehicleById.getValue(reminder.vehicleId).vehicleType,
                typeById.getValue(reminder.serviceTypeId).vehicleType)
        }
        val normalized = reminders.associate { reminder ->
            (vehicleById.getValue(reminder.vehicleId).publicId to typeById.getValue(reminder.serviceTypeId).publicId) to
                reminder.copy(vehicleId = 0, serviceTypeId = 0)
        }
        assertEquals(reminders.size, normalized.size)
        return normalized
    }

    private fun assertInitialStates(history: MaintenanceHistoryFixture.History, states: Map<Pair<String, String>, DerivedMaintenanceReminder>) {
        val oil = states.getValue(history.car.publicId to CAR_OIL)
        assertEquals(MaintenanceBaseline(4000, LocalDate.parse("2025-01-01")), oil.baseline)
        assertFalse(oil.usesConfiguredMileageBaseline)
        assertEquals(1000L, oil.intervalMileage)
        assertEquals(2L, oil.intervalMonths)
        assertEquals(MaintenanceReminderType.INSPECT, oil.reminderType)
        assertEquals(9, oil.sortOrder)
        assertEquals(MaintenanceReminderCalculation(MaintenanceReminderStatus.DUE, 5000,
            LocalDate.parse("2025-03-01"), 0, 45), oil.calculation)
        val carCustom = states.getValue(history.car.publicId to history.carCustom.publicId)
        assertEquals(MaintenanceBaseline(3500, LocalDate.parse("2024-12-15")), carCustom.baseline)
        assertEquals(MaintenanceReminderCalculation(MaintenanceReminderStatus.OVERDUE, 4277,
            LocalDate.parse("2025-03-15"), -723, 59), carCustom.calculation)
        assertEquals(MaintenanceReminderType.ESTIMATE, carCustom.reminderType)
        assertEquals(8, carCustom.sortOrder)
        val motorcycleCustom = states.getValue(history.motorcycle.publicId to history.motorcycleCustom.publicId)
        assertEquals(MaintenanceBaseline(4500, LocalDate.parse("2024-12-20")), motorcycleCustom.baseline)
        assertEquals(MaintenanceReminderCalculation(MaintenanceReminderStatus.UPCOMING, 5250,
            LocalDate.parse("2025-01-20"), 250, 5), motorcycleCustom.calculation)
        val unknown = states.getValue(history.motorcycle.publicId to MOTORCYCLE_OIL)
        assertEquals(MaintenanceBaseline(null, null), unknown.baseline)
        assertFalse(unknown.usesConfiguredMileageBaseline)
        assertEquals(MaintenanceReminderCalculation(MaintenanceReminderStatus.UNKNOWN, null, null, null, null),
            unknown.calculation)
        val configured = states.getValue(history.car.publicId to "builtin-car-air-filter")
        assertEquals(MaintenanceBaseline(0, null), configured.baseline)
        assertTrue(configured.usesConfiguredMileageBaseline)
        assertEquals(MaintenanceReminderCalculation(MaintenanceReminderStatus.NORMAL, 40000, null, 35000, null),
            configured.calculation)
        assertFalse(states.keys.any { it.first == history.archivedVehicle.publicId })
        assertFalse(states.keys.any { it.second in setOf(history.disabledType.publicId, history.archivedType.publicId) })
    }

    private fun assertCompletionChain(archive: File, expected: Map<String, String?>) {
        val data = BackupArchiveReader.read(archive).data
        val items = data.serviceItems.associateBy { it.publicId }
        val records = data.serviceRecords.associateBy { it.publicId }
        val reminders = data.reminders.filter { it.source == ReminderSource.SERVICE_ITEM.name }
        val actual = reminders.associate { reminder ->
            val item = items.getValue(requireNotNull(reminder.sourceServiceItemPublicId))
            assertEquals(reminder.completedByServiceRecordPublicId != null, reminder.completedAt != null)
            records.getValue(item.serviceRecordPublicId).title to
                reminder.completedByServiceRecordPublicId?.let { records.getValue(it).title }
        }
        assertEquals(reminders.size, actual.size)
        assertEquals(expected, actual)
    }

    private val initialCompletionChain = mapOf("car-oil-first" to "car-oil-last", "car-oil-last" to null,
        "car-custom" to null, "moto-custom" to null, "archived-oil" to null)

}
