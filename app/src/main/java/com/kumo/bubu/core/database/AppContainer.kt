package com.kumo.bubu.core.database

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.kumo.bubu.R
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.repository.OfflineFuelRepository
import com.kumo.bubu.data.repository.OfflineServiceRepository
import com.kumo.bubu.data.repository.OfflineVehicleRepository
import com.kumo.bubu.data.repository.OfflineExpenseRepository
import com.kumo.bubu.data.repository.OfflineReportRepository
import com.kumo.bubu.data.repository.OfflineReminderRepository
import com.kumo.bubu.data.repository.AutomaticReminderLabels
import com.kumo.bubu.data.repository.DataStoreReminderNotificationSettings
import com.kumo.bubu.data.repository.DataStoreBackupReminderSettings
import com.kumo.bubu.data.repository.DataStoreStatutoryReminderSettings
import com.kumo.bubu.data.repository.DataStoreReportLayoutSettings
import com.kumo.bubu.data.repository.CpcFuelPriceRepository
import com.kumo.bubu.data.repository.OfflineCsvExportRepository
import com.kumo.bubu.data.repository.OfflineBackupRepository
import com.kumo.bubu.data.repository.OfflineRestoreRepository
import com.kumo.bubu.domain.repository.ExpenseRepository
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import com.kumo.bubu.domain.repository.FuelPriceRepository
import com.kumo.bubu.domain.repository.ReportRepository
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.ReminderNotificationSettings
import com.kumo.bubu.domain.repository.BackupReminderSettings
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import com.kumo.bubu.domain.repository.CsvExportRepository
import com.kumo.bubu.domain.repository.BackupRepository
import com.kumo.bubu.domain.repository.RestoreRepository
import com.kumo.bubu.domain.repository.ReportLayoutSettings
import com.kumo.bubu.core.notification.ReminderNotificationScheduler
import com.kumo.bubu.core.notification.ReminderNotifications
import com.kumo.bubu.core.notification.WorkManagerReminderNotificationScheduler
import com.kumo.bubu.core.notification.BackupReminderScheduler
import com.kumo.bubu.core.notification.WorkManagerBackupReminderScheduler
import com.kumo.bubu.domain.model.BuiltInServiceTypeSeed
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import java.time.LocalDate

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val serviceMigrationLabels = ServiceMigrationLabels(
        maintenanceTitle = applicationContext.getString(R.string.migration_service_title_maintenance),
        repairTitle = applicationContext.getString(R.string.migration_service_title_repair),
        inspectionTitle = applicationContext.getString(R.string.migration_service_title_inspection),
        genericTitle = applicationContext.getString(R.string.migration_service_title_generic),
        laborItem = applicationContext.getString(R.string.migration_service_item_labor),
        legacyTotalAdjustmentItem = applicationContext.getString(
            R.string.migration_service_item_total_adjustment,
        ),
    )
    private val builtInServiceTypeSeeds = listOf(
        carSeed("engine-oil", R.string.service_default_car_engine_oil, true),
        carSeed("oil-filter", R.string.service_default_car_oil_filter, true),
        carSeed("air-filter", R.string.service_default_car_air_filter, true),
        carSeed("cabin-filter", R.string.service_default_car_cabin_filter, true),
        carSeed("brake-fluid", R.string.service_default_car_brake_fluid, true),
        carSeed("spark-plugs", R.string.service_default_car_spark_plugs, true),
        carSeed("transmission-oil", R.string.service_default_car_transmission_oil, true),
        carSeed("tires", R.string.service_default_car_tires, true),
        carSeed("coolant", R.string.service_default_car_coolant), carSeed("brake-pads", R.string.service_default_car_brake_pads),
        carSeed("battery", R.string.service_default_car_battery), carSeed("wipers", R.string.service_default_car_wipers),
        carSeed("alignment", R.string.service_default_car_alignment), carSeed("balancing", R.string.service_default_car_balancing),
        carSeed("ac-system", R.string.service_default_car_ac_system), carSeed("throttle", R.string.service_default_car_throttle),
        carSeed("injector", R.string.service_default_car_injector), carSeed("carbon", R.string.service_default_car_carbon),
        carSeed("engine-belt", R.string.service_default_car_engine_belt), carSeed("shocks", R.string.service_default_car_shocks),
        carSeed("chassis", R.string.service_default_car_chassis), carSeed("other", R.string.built_in_service_type_other),
        motorcycleSeed("engine-oil", R.string.service_default_motorcycle_engine_oil, true),
        motorcycleSeed("gear-oil", R.string.service_default_motorcycle_gear_oil, true),
        motorcycleSeed("air-filter", R.string.service_default_motorcycle_air_filter, true),
        motorcycleSeed("spark-plugs", R.string.service_default_motorcycle_spark_plugs, true),
        motorcycleSeed("brake-pads", R.string.service_default_motorcycle_brake_pads, true),
        motorcycleSeed("drive-belt", R.string.service_default_motorcycle_drive_belt, true),
        motorcycleSeed("variator", R.string.service_default_motorcycle_variator, true),
        motorcycleSeed("tires", R.string.service_default_motorcycle_tires, true),
        motorcycleSeed("oil-filter", R.string.service_default_motorcycle_oil_filter), motorcycleSeed("brake-fluid", R.string.service_default_motorcycle_brake_fluid),
        motorcycleSeed("drive-clean", R.string.service_default_motorcycle_drive_clean), motorcycleSeed("clutch", R.string.service_default_motorcycle_clutch),
        motorcycleSeed("bell", R.string.service_default_motorcycle_bell), motorcycleSeed("battery", R.string.service_default_motorcycle_battery),
        motorcycleSeed("throttle", R.string.service_default_motorcycle_throttle), motorcycleSeed("injector", R.string.service_default_motorcycle_injector),
        motorcycleSeed("valve", R.string.service_default_motorcycle_valve), motorcycleSeed("front-suspension", R.string.service_default_motorcycle_front_suspension),
        motorcycleSeed("rear-suspension", R.string.service_default_motorcycle_rear_suspension), motorcycleSeed("coolant", R.string.service_default_motorcycle_coolant),
        motorcycleSeed("brake-system", R.string.service_default_motorcycle_brake_system), motorcycleSeed("other", R.string.built_in_service_type_other),
    )
    internal val database = Room.databaseBuilder(
        applicationContext,
        BuBuDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(
        BuBuDatabase.MIGRATION_1_2,
        BuBuDatabase.MIGRATION_2_3,
        BuBuDatabase.MIGRATION_3_4,
        BuBuDatabase.migration4To5(serviceMigrationLabels),
        BuBuDatabase.MIGRATION_5_6,
        BuBuDatabase.MIGRATION_6_7,
        BuBuDatabase.MIGRATION_7_8,
        BuBuDatabase.MIGRATION_8_9,
        BuBuDatabase.MIGRATION_9_10,
    )
        .build()

    private val serviceAttachmentStore = PrivateAttachmentStore(
        context = applicationContext,
        fallbackDisplayNameStem = applicationContext.getString(
            R.string.service_attachment_default_name,
        ),
    )

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val preferences = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = { applicationContext.preferencesDataStoreFile(PREFERENCES_NAME) },
    )

    val vehicleRepository: VehicleRepository = OfflineVehicleRepository(
        vehicleDao = database.vehicleDao(),
        fuelRecordDao = database.fuelRecordDao(),
        serviceRecordDao = database.serviceRecordDao(),
        preferences = preferences,
    )

    val fuelRepository: FuelRepository = OfflineFuelRepository(
        database = database,
        vehicleDao = database.vehicleDao(),
        fuelRecordDao = database.fuelRecordDao(),
        serviceRecordDao = database.serviceRecordDao(),
        preferences = preferences,
    )

    val fuelPriceRepository: FuelPriceRepository = CpcFuelPriceRepository()

    private val offlineServiceRepository = OfflineServiceRepository(
        database = database,
        vehicleDao = database.vehicleDao(),
        fuelRecordDao = database.fuelRecordDao(),
        serviceRecordDao = database.serviceRecordDao(),
        serviceItemDao = database.serviceItemDao(),
        serviceTypeDao = database.serviceTypeDao(),
        attachmentStore = serviceAttachmentStore,
        builtInServiceTypeSeeds = builtInServiceTypeSeeds,
    )
    val serviceRepository: ServiceRepository = offlineServiceRepository

    val reportRepository: ReportRepository = OfflineReportRepository(database.reportDao())
    val reportLayoutSettings: ReportLayoutSettings = DataStoreReportLayoutSettings(preferences)

    val csvExportRepository: CsvExportRepository = OfflineCsvExportRepository(
        context = applicationContext,
        vehicleDao = database.vehicleDao(),
        fuelRecordDao = database.fuelRecordDao(),
        serviceRecordDao = database.serviceRecordDao(),
        serviceItemDao = database.serviceItemDao(),
        expenseRecordDao = database.expenseRecordDao(),
        reminderDao = database.vehicleReminderDao(),
        serviceAttachmentDao = database.serviceAttachmentDao(),
    )

    val backupReminderSettings: BackupReminderSettings = DataStoreBackupReminderSettings(preferences)
    val backupReminderScheduler: BackupReminderScheduler =
        WorkManagerBackupReminderScheduler(applicationContext)

    private val offlineBackupRepository = OfflineBackupRepository(
        context = applicationContext,
        database = database,
        attachmentStore = serviceAttachmentStore,
        backupReminderSettings = backupReminderSettings,
    )
    val backupRepository: BackupRepository = offlineBackupRepository

    val restoreRepository: RestoreRepository = OfflineRestoreRepository(
        context = applicationContext,
        database = database,
        attachmentStore = serviceAttachmentStore,
        backupRepository = offlineBackupRepository,
    )

    val statutoryReminderSettings: StatutoryReminderSettings =
        DataStoreStatutoryReminderSettings(preferences)

    val reminderRepository: ReminderRepository = OfflineReminderRepository(
        database = database,
        reminderDao = database.vehicleReminderDao(),
        vehicleDao = database.vehicleDao(),
        fuelRecordDao = database.fuelRecordDao(),
        serviceRecordDao = database.serviceRecordDao(),
        statutoryReminderSettings = statutoryReminderSettings,
        labels = AutomaticReminderLabels(
            licenseTax = applicationContext.getString(R.string.reminders_title_license_tax),
            roadMaintenanceFee = applicationContext.getString(R.string.reminders_title_road_maintenance_fee),
            periodicInspection = applicationContext.getString(R.string.reminders_title_periodic_inspection),
            periodicInspectionSecondary = applicationContext.getString(
                R.string.reminders_title_periodic_inspection_secondary,
            ),
        ),
    )

    val expenseRepository: ExpenseRepository = OfflineExpenseRepository(
        database,
        database.vehicleDao(),
        database.expenseRecordDao(),
        database.vehicleReminderDao(),
        reminderRepository,
    )

    val reminderNotificationSettings: ReminderNotificationSettings =
        DataStoreReminderNotificationSettings(preferences)
    val reminderNotificationScheduler: ReminderNotificationScheduler =
        WorkManagerReminderNotificationScheduler(applicationContext)

    init {
        applicationScope.launch {
            offlineServiceRepository.retryPendingAttachmentDeletions()
        }
        applicationScope.launch {
            runCatching { reminderRepository.refreshAutomaticReminders(LocalDate.now()) }
                .onFailure { error -> Log.e("AppContainer", "Unable to refresh automatic reminders.", error) }
        }
        applicationScope.launch {
            backupReminderSettings.observeEnabled().collect { enabled ->
                if (enabled) backupReminderScheduler.scheduleDailyCheck()
                else backupReminderScheduler.cancelDailyCheck()
            }
        }
        applicationScope.launch {
            combine(
                database.vehicleReminderDao().observeAll(),
                database.vehicleDao().observeAll(),
                reminderNotificationSettings.observeEnabled(),
                statutoryReminderSettings.observeTaxAndFeeEnabled(),
            ) { reminders, vehicles, notificationsEnabled, taxAndFeeEnabled ->
                ReminderNotificationReconcileState(
                    reminders = reminders,
                    archivedVehicleIds = vehicles.filter { it.isArchived }.mapTo(mutableSetOf()) { it.id },
                    notificationsEnabled = notificationsEnabled,
                    taxAndFeeEnabled = taxAndFeeEnabled,
                )
            }.collect { state ->
                if (!state.notificationsEnabled) {
                    ReminderNotifications.cancelAll(applicationContext)
                } else {
                    ReminderNotifications.cancelDeleted(
                        applicationContext,
                        state.reminders.map(VehicleReminderEntity::id),
                    )
                    val todayEpochDay = LocalDate.now().toEpochDay()
                    state.reminders
                        .filter { reminder ->
                            reminder.completedAt != null || !reminder.isEnabled ||
                                reminder.vehicleId in state.archivedVehicleIds ||
                                (reminder.snoozedUntilEpochDay ?: Long.MIN_VALUE) > todayEpochDay ||
                                (!state.taxAndFeeEnabled && reminder.source.isTaxOrFee())
                        }
                        .forEach { reminder ->
                            ReminderNotifications.cancel(applicationContext, reminder.id)
                        }
                }
            }
        }
    }

    private fun serviceTypeSeed(key: String, @StringRes nameResource: Int, vehicleType: VehicleType, isQuickPick: Boolean = false) = BuiltInServiceTypeSeed(
        key = key,
        displayName = applicationContext.getString(nameResource),
        vehicleType = vehicleType,
        isQuickPick = isQuickPick,
    )

    private fun carSeed(key: String, @StringRes nameResource: Int, quick: Boolean = false) =
        serviceTypeSeed(key, nameResource, VehicleType.CAR, quick)

    private fun motorcycleSeed(key: String, @StringRes nameResource: Int, quick: Boolean = false) =
        serviceTypeSeed(key, nameResource, VehicleType.MOTORCYCLE, quick)

    private companion object {
        const val DATABASE_NAME = "bubu.db"
        const val PREFERENCES_NAME = "vehicle_preferences"
    }
}

private data class ReminderNotificationReconcileState(
    val reminders: List<VehicleReminderEntity>,
    val archivedVehicleIds: Set<Long>,
    val notificationsEnabled: Boolean,
    val taxAndFeeEnabled: Boolean,
)

private fun ReminderSource.isTaxOrFee(): Boolean =
    this == ReminderSource.LICENSE_TAX || this == ReminderSource.ROAD_MAINTENANCE_FEE
