package com.kumo.bubu.data.repository

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kumo.bubu.R
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.domain.model.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first

/** Coherent history created through real repositories in isolated Room/DataStore storage. */
internal class MaintenanceHistoryFixture(context: Context) {
    val storage = RestoreSafetyFixture(context)
    val today: LocalDate = LocalDate.of(2025, 1, 15)
    private val preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sourcePreferences = PreferenceDataStoreFactory.create(scope = preferencesScope) {
        File(storage.directory, "source.preferences_pb")
    }
    private val targetPreferences = PreferenceDataStoreFactory.create(scope = preferencesScope) {
        File(storage.directory, "target.preferences_pb")
    }
    val sourceVehicles = OfflineVehicleRepository(storage.source.vehicleDao(), storage.source.fuelRecordDao(),
        storage.source.serviceRecordDao(), sourcePreferences)
    val targetVehicles = OfflineVehicleRepository(storage.target.vehicleDao(), storage.target.fuelRecordDao(),
        storage.target.serviceRecordDao(), targetPreferences)
    private val sourceFuel = OfflineFuelRepository(storage.source, storage.source.vehicleDao(),
        storage.source.fuelRecordDao(), storage.source.serviceRecordDao(), sourcePreferences)
    val sourceServices = services(storage.source)
    val targetServices = services(storage.target)

    suspend fun close() {
        preferencesScope.coroutineContext[Job]?.cancelAndJoin()
        storage.close()
    }

    suspend fun createHistory(): History {
        sourceServices.ensureDefaultServiceTypes()
        val car = createVehicle("active car", VehicleType.CAR)
        val motorcycle = createVehicle("active motorcycle", VehicleType.MOTORCYCLE)
        val archived = createVehicle("archived car", VehicleType.CAR)
        sourceVehicles.setNewVehicleMaintenanceBaseline(car.id, true)
        val oil = sourceServices.observeServiceTypes().first().single { it.publicId == CAR_OIL }
        val carCustom = createType("car custom maintenance", VehicleType.CAR)
        val motorcycleCustom = createType("motorcycle custom maintenance", VehicleType.MOTORCYCLE)
        val disabledType = createType("disabled maintenance", VehicleType.CAR)
        val archivedType = createType("archived maintenance", VehicleType.CAR)
        listOf(
            ServiceReminderPreferenceInput(car.id, oil.id, true, 1000, 2,
                MaintenanceReminderType.INSPECT, baseOdometerKm = 0, sortOrder = 9),
            ServiceReminderPreferenceInput(car.id, carCustom.id, true, 777, 3,
                MaintenanceReminderType.ESTIMATE, sortOrder = 8),
            ServiceReminderPreferenceInput(motorcycle.id, motorcycleCustom.id, true, 750, 1,
                MaintenanceReminderType.ESTIMATE, sortOrder = 7),
            ServiceReminderPreferenceInput(car.id, disabledType.id, false, 200, 1, sortOrder = 1),
            ServiceReminderPreferenceInput(car.id, archivedType.id, true, 333, 4, sortOrder = 2),
        ).forEach { sourceServices.saveServiceReminderPreference(it) }
        sourceServices.setServiceTypeArchived(archivedType.id, true)
        val receipt = storage.store.restoreManagedBytes(Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==",
            Base64.DEFAULT), "maintenance-receipt.png", "image/png")
        sourceServices.createServiceRecord(workOrder(car.id, oil, "car-oil-first", "2024-12-01", 1000)
            .copy(attachments = listOf(ServiceAttachmentInput(relativePath = receipt.relativePath,
                displayName = receipt.displayName, mimeType = receipt.mimeType, isStaged = true))))
        sourceServices.createServiceRecord(workOrder(car.id, oil, "car-oil-last", "2025-01-01", 4000))
        sourceServices.createServiceRecord(workOrder(car.id, carCustom, "car-custom", "2024-12-15", 3500))
        sourceServices.createServiceRecord(workOrder(motorcycle.id, motorcycleCustom, "moto-custom", "2024-12-20", 4500))
        sourceServices.createServiceRecord(workOrder(archived.id, oil, "archived-oil", "2024-12-01", 3000))
        sourceVehicles.setVehicleArchived(archived.id, true)
        return History(requireNotNull(sourceVehicles.getVehicle(car.id)), motorcycle,
            requireNotNull(sourceVehicles.getVehicle(archived.id)), carCustom, motorcycleCustom,
            disabledType, archivedType)
    }

    private fun services(database: BuBuDatabase) = OfflineServiceRepository(database,
        database.vehicleDao(), database.fuelRecordDao(), database.serviceRecordDao(),
        database.serviceItemDao(), database.serviceTypeDao(), attachmentStore = storage.store,
        builtInServiceTypeSeeds = listOf(
            BuiltInServiceTypeSeed("engine-oil", storage.context.getString(R.string.service_default_car_engine_oil), VehicleType.CAR),
            BuiltInServiceTypeSeed("air-filter", storage.context.getString(R.string.service_default_car_air_filter), VehicleType.CAR),
            BuiltInServiceTypeSeed("engine-oil", storage.context.getString(R.string.service_default_motorcycle_engine_oil), VehicleType.MOTORCYCLE),
        ))

    fun vehicleInput(name: String, type: VehicleType) = VehicleInput(name, type,
        motorcycleClass = MotorcycleClass.ORDINARY_HEAVY.takeIf { type == VehicleType.MOTORCYCLE },
        trackingStartDateEpochDay = LocalDate.of(2024, 1, 1).toEpochDay(), trackingStartOdometerKm = 0)

    private suspend fun createVehicle(name: String, type: VehicleType): Vehicle {
        val id = sourceVehicles.createVehicle(vehicleInput(name, type))
        // Independent later mileage observation survives service backfill, edits and deletions.
        sourceFuel.createFuelRecord(FuelRecordInput(id, today.toEpochDay(), null, 5000,
            fuelVolumeMl = 10000, pricePerLiterMilli = 32000, totalCostTwd = 320,
            isFullTank = true, fuelProduct = FuelProduct.GASOLINE_95))
        return requireNotNull(sourceVehicles.getVehicle(id))
    }

    private suspend fun createType(name: String, type: VehicleType): ServiceType {
        val id = sourceServices.createServiceType(ServiceTypeInput(name, type))
        return sourceServices.observeServiceTypes().first().single { it.id == id }
    }

    fun workOrder(vehicleId: Long, type: ServiceType, title: String, date: String,
        mileage: Long) = ServiceRecordInput(vehicleId, LocalDate.parse(date).toEpochDay(), null,
        mileage, ServiceRecordType.MAINTENANCE, title, PaymentMethod.CASH, totalCostTwd = 500,
        items = listOf(ServiceItemInput(type.name, type.id, quantityMilli = 1000, unitPriceTwd = 500)))

    data class History(val car: Vehicle, val motorcycle: Vehicle, val archivedVehicle: Vehicle,
        val carCustom: ServiceType, val motorcycleCustom: ServiceType, val disabledType: ServiceType,
        val archivedType: ServiceType)

    companion object {
        const val CAR_OIL = "builtin-car-engine-oil"
        const val MOTORCYCLE_OIL = "builtin-motorcycle-engine-oil"
    }
}
