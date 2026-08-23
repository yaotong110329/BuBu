package com.kumo.bubu.data.repository

import androidx.room.withTransaction
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.dao.FuelRecordDao
import com.kumo.bubu.data.local.dao.ServiceRecordDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.local.dao.VehicleReminderDao
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.MileageObservation
import com.kumo.bubu.domain.model.MileageObservationSource
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.StatutoryReminderKind
import com.kumo.bubu.domain.model.StatutoryVehicleProfile
import com.kumo.bubu.domain.model.taiwanStatutoryRuleVerifiedDate
import com.kumo.bubu.domain.model.estimateMileageNotification
import com.kumo.bubu.domain.model.taiwanStatutoryReminderPlans
import com.kumo.bubu.domain.model.validated
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AutomaticReminderLabels(
    val licenseTax: String,
    val roadMaintenanceFee: String,
    val periodicInspection: String,
    val periodicInspectionSecondary: String,
)

class OfflineReminderRepository(
    private val database: BuBuDatabase,
    private val reminderDao: VehicleReminderDao,
    private val vehicleDao: VehicleDao,
    private val fuelRecordDao: FuelRecordDao,
    private val serviceRecordDao: ServiceRecordDao,
    private val statutoryReminderSettings: StatutoryReminderSettings,
    private val labels: AutomaticReminderLabels,
) : ReminderRepository {
    override fun observeReminders(): Flow<List<com.kumo.bubu.domain.model.VehicleReminder>> =
        reminderDao.observeAll().map { reminders -> reminders.map(VehicleReminderEntity::toDomain) }

    override suspend fun createManualReminder(input: ManualReminderInput): Long {
        val validInput = input.validated()
        val vehicle = requireNotNull(vehicleDao.getById(validInput.vehicleId)) { "Vehicle does not exist." }
        require(!vehicle.isArchived) { "Archived vehicle cannot receive reminders." }
        val now = System.currentTimeMillis()
        return reminderDao.insert(
            VehicleReminderEntity(
                publicId = UUID.randomUUID().toString(),
                vehicleId = validInput.vehicleId,
                source = ReminderSource.MANUAL,
                sourceServiceItemId = null,
                title = validInput.title,
                dueOdometerKm = validInput.dueOdometerKm,
                dueDateEpochDay = validInput.dueDateEpochDay,
                isEnabled = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun completeReminder(id: Long) {
        val existing = requireNotNull(reminderDao.getById(id)) { "Reminder does not exist." }
        require(existing.source != ReminderSource.SERVICE_ITEM) {
            "Service reminders are completed by a service record."
        }
        val now = System.currentTimeMillis()
        reminderDao.update(
            existing.copy(
                completedByServiceRecordId = null,
                completedByExpenseRecordId = null,
                completedAt = now,
                snoozedUntilEpochDay = null,
                lastNotifiedStatus = null,
                lastNotifiedTrigger = null,
                updatedAt = now,
            ),
        )
    }

    override suspend fun snoozeReminder(id: Long, untilEpochDay: Long) {
        val existing = requireNotNull(reminderDao.getById(id)) { "Reminder does not exist." }
        require(existing.completedAt == null) { "Completed reminders cannot be snoozed." }
        reminderDao.update(
            existing.copy(
                snoozedUntilEpochDay = untilEpochDay,
                lastNotifiedStatus = null,
                lastNotifiedTrigger = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setReminderEnabled(id: Long, enabled: Boolean) {
        val existing = requireNotNull(reminderDao.getById(id)) { "Reminder does not exist." }
        reminderDao.update(existing.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun setTaxAndFeeRemindersEnabled(enabled: Boolean) {
        val previous = statutoryReminderSettings.observeTaxAndFeeEnabled().first()
        statutoryReminderSettings.setTaxAndFeeEnabled(enabled)
        try {
            reminderDao.updateTaxAndFeeEnabled(enabled, System.currentTimeMillis())
        } catch (error: Throwable) {
            runCatching { statutoryReminderSettings.setTaxAndFeeEnabled(previous) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        }
    }

    override suspend fun ensureStatutoryReminder(
        vehicleId: Long,
        cycleYear: Int,
        source: ReminderSource,
    ) {
        require(source == ReminderSource.LICENSE_TAX || source == ReminderSource.ROAD_MAINTENANCE_FEE) {
            "Only automatic tax and fee reminders can be ensured for an expense."
        }
        val defaultTaxAndFeeEnabled = statutoryReminderSettings.observeTaxAndFeeEnabled().first()
        database.withTransaction {
            val vehicle = requireNotNull(vehicleDao.getById(vehicleId)) { "Vehicle does not exist." }
            syncStatutoryReminders(
                vehicle = vehicle,
                cycleDate = LocalDate.of(cycleYear, 1, 1),
                defaultTaxAndFeeEnabled = defaultTaxAndFeeEnabled,
                now = System.currentTimeMillis(),
                sourceFilter = source,
            )
        }
    }

    override suspend fun refreshAutomaticReminders(today: LocalDate) {
        val defaultTaxAndFeeEnabled = statutoryReminderSettings.observeTaxAndFeeEnabled().first()
        database.withTransaction {
            val vehicles = vehicleDao.getAll()
            val now = System.currentTimeMillis()
            vehicles.forEach { vehicle ->
                syncStatutoryReminders(vehicle, today, defaultTaxAndFeeEnabled, now)
            }
            refreshMileageForecasts(today)
        }
    }

    private suspend fun syncStatutoryReminders(
        vehicle: VehicleEntity,
        cycleDate: LocalDate,
        defaultTaxAndFeeEnabled: Boolean,
        now: Long,
        sourceFilter: ReminderSource? = null,
    ) {
        val profile = StatutoryVehicleProfile(
            vehicleId = vehicle.id,
            vehicleType = vehicle.vehicleType,
            motorcycleClass = vehicle.motorcycleClass,
            engineDisplacementCc = vehicle.engineDisplacementCc,
            manufactureYear = vehicle.manufactureYear,
            primaryInspectionMonthDay = vehicle.primaryInspectionMonthDay,
            secondaryInspectionMonthDay = vehicle.secondaryInspectionMonthDay,
            isArchived = vehicle.isArchived,
        )
        val plans = taiwanStatutoryReminderPlans(profile, cycleDate)
            .filter { plan -> sourceFilter == null || plan.kind.toReminderSource() == sourceFilter }
        val plannedKeys = plans.mapTo(mutableSetOf()) { it.automaticKey }
        plans.forEach { plan ->
            val existing = reminderDao.getByAutomaticKey(plan.automaticKey)
            if (existing == null) {
                val inheritedEnabled = reminderDao.getLatestByAutomaticKeyPrefix(
                    "statutory:${vehicle.id}:${plan.kind.name}:",
                )?.isEnabled ?: if (plan.kind.isTaxOrFee()) defaultTaxAndFeeEnabled else true
                reminderDao.insert(
                    VehicleReminderEntity(
                        publicId = UUID.randomUUID().toString(),
                        vehicleId = vehicle.id,
                        source = plan.kind.toReminderSource(),
                        sourceServiceItemId = null,
                        title = plan.kind.title(labels),
                        dueOdometerKm = null,
                        dueDateEpochDay = plan.dueDateEpochDay,
                        referenceDateEpochDay = plan.referenceDateEpochDay,
                        isEnabled = inheritedEnabled,
                        createdAt = now,
                        updatedAt = now,
                        automaticKey = plan.automaticKey,
                        ruleVersion = STATUTORY_RULE_VERSION,
                        ruleVerifiedEpochDay = ruleVerifiedEpochDay,
                    ),
                )
            } else if (existing.completedAt == null) {
                reminderDao.update(
                    existing.copy(
                        source = plan.kind.toReminderSource(),
                        title = plan.kind.title(labels),
                        dueDateEpochDay = plan.dueDateEpochDay,
                        referenceDateEpochDay = plan.referenceDateEpochDay,
                        isEnabled = if (plan.kind.isTaxOrFee() && !defaultTaxAndFeeEnabled) {
                            false
                        } else {
                            existing.isEnabled
                        },
                        ruleVersion = STATUTORY_RULE_VERSION,
                        ruleVerifiedEpochDay = ruleVerifiedEpochDay,
                        updatedAt = now,
                    ),
                )
            }
        }
        if (sourceFilter == null) {
            val currentPrefix = "statutory:${vehicle.id}:"
            reminderDao.getForVehicle(vehicle.id)
                .filter { reminder ->
                    val automaticKey = reminder.automaticKey
                    reminder.completedAt == null && automaticKey != null &&
                        automaticKey.startsWith(currentPrefix) &&
                        automaticKey.endsWith(":${cycleDate.year}") &&
                        automaticKey !in plannedKeys
                }
                .forEach { reminderDao.deleteById(it.id) }
        }
    }

    private suspend fun refreshMileageForecasts(today: LocalDate) {
        val vehicles = vehicleDao.getAll().associateBy { it.id }
        val observationsByVehicle = mutableMapOf<Long, List<MileageObservation>>()
        reminderDao.getAll().forEach { reminder ->
            val dueOdometerKm = reminder.dueOdometerKm ?: return@forEach
            val vehicle = vehicles[reminder.vehicleId]
            if (vehicle == null || vehicle.isArchived || reminder.completedAt != null || !reminder.isEnabled) {
                if (reminder.estimatedNotificationEpochDay != null) {
                    reminderDao.updateMileageForecast(reminder.id, null, System.currentTimeMillis())
                }
                return@forEach
            }
            val observations = observationsByVehicle.getOrPut(vehicle.id) {
                buildList {
                    add(
                        MileageObservation(
                            vehicle.id,
                            vehicle.trackingStartDateEpochDay,
                            vehicle.trackingStartOdometerKm,
                            source = MileageObservationSource.TRACKING_START,
                        ),
                    )
                    fuelRecordDao.getForVehicleInRecordOrder(vehicle.id).forEach { record ->
                        add(
                            MileageObservation(
                                vehicle.id,
                                record.dateEpochDay,
                                record.odometerKm,
                                record.timeMinuteOfDay,
                                MileageObservationSource.FUEL,
                                record.sequenceInDay,
                            ),
                        )
                    }
                    serviceRecordDao.getForVehicleInRecordOrder(vehicle.id).forEach { record ->
                        add(
                            MileageObservation(
                                vehicle.id,
                                record.dateEpochDay,
                                record.odometerKm,
                                record.timeMinuteOfDay,
                                MileageObservationSource.SERVICE,
                                record.sequenceInDay,
                            ),
                        )
                    }
                }
            }
            val estimate = estimateMileageNotification(vehicle.id, dueOdometerKm, today, observations)
            if (estimate?.notificationDateEpochDay != reminder.estimatedNotificationEpochDay) {
                reminderDao.updateMileageForecast(
                    reminder.id,
                    estimate?.notificationDateEpochDay,
                    System.currentTimeMillis(),
                )
            }
        }
    }

    private fun StatutoryReminderKind.toReminderSource(): ReminderSource = when (this) {
        StatutoryReminderKind.LICENSE_TAX -> ReminderSource.LICENSE_TAX
        StatutoryReminderKind.ROAD_MAINTENANCE_FEE -> ReminderSource.ROAD_MAINTENANCE_FEE
        StatutoryReminderKind.PERIODIC_INSPECTION_PRIMARY,
        StatutoryReminderKind.PERIODIC_INSPECTION_SECONDARY,
        -> ReminderSource.PERIODIC_INSPECTION
    }

    private fun StatutoryReminderKind.title(labels: AutomaticReminderLabels): String = when (this) {
        StatutoryReminderKind.LICENSE_TAX -> labels.licenseTax
        StatutoryReminderKind.ROAD_MAINTENANCE_FEE -> labels.roadMaintenanceFee
        StatutoryReminderKind.PERIODIC_INSPECTION_PRIMARY -> labels.periodicInspection
        StatutoryReminderKind.PERIODIC_INSPECTION_SECONDARY -> labels.periodicInspectionSecondary
    }

    private fun StatutoryReminderKind.isTaxOrFee(): Boolean =
        this == StatutoryReminderKind.LICENSE_TAX || this == StatutoryReminderKind.ROAD_MAINTENANCE_FEE

    private companion object {
        const val STATUTORY_RULE_VERSION = 1
        val ruleVerifiedEpochDay: Long = taiwanStatutoryRuleVerifiedDate.toEpochDay()
    }
}
