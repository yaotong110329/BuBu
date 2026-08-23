package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kumo.bubu.data.local.dao.FuelRecordDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.local.dao.ServiceRecordDao
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.data.mapper.toNewEntity
import com.kumo.bubu.data.mapper.toUpdatedEntity
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleGarage
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.validated
import com.kumo.bubu.domain.repository.VehicleRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineVehicleRepository(
    private val vehicleDao: VehicleDao,
    private val fuelRecordDao: FuelRecordDao,
    private val serviceRecordDao: ServiceRecordDao,
    private val preferences: DataStore<Preferences>,
) : VehicleRepository {
    override fun observeVehicles(): Flow<List<Vehicle>> =
        vehicleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeGarage(): Flow<VehicleGarage> = combine(
        observeVehicles(),
        preferences.data.map { it[CURRENT_VEHICLE_PUBLIC_ID] },
    ) { vehicles, storedPublicId ->
        VehicleGarage(
            vehicles = vehicles,
            currentVehiclePublicId = resolveCurrentVehiclePublicId(vehicles, storedPublicId),
        )
    }

    override suspend fun getVehicle(id: Long): Vehicle? = vehicleDao.getById(id)?.toDomain()

    override suspend fun createVehicle(input: VehicleInput): Long {
        val validInput = input.validated()
        val id = vehicleDao.insert(
            validInput.toNewEntity(
                publicId = UUID.randomUUID().toString(),
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
        reconcileCurrentVehicleSelection()
        return id
    }

    override suspend fun updateVehicle(id: Long, input: VehicleInput) {
        val existing = requireNotNull(vehicleDao.getById(id)) { "Vehicle does not exist." }
        val validInput = input.validated()
        val now = System.currentTimeMillis()
        val currentOdometer = maxOf(
            validInput.trackingStartOdometerKm,
            fuelRecordDao.maxOdometerKm(id) ?: validInput.trackingStartOdometerKm,
            serviceRecordDao.maxOdometerKm(id) ?: validInput.trackingStartOdometerKm,
        )
        vehicleDao.update(
            validInput.toUpdatedEntity(existing, now).copy(currentOdometerKm = currentOdometer),
        )
    }

    override suspend fun selectCurrentVehicle(publicId: String) {
        val vehicle = observeVehicles().first().firstOrNull { it.publicId == publicId }
        requireNotNull(vehicle) { "Vehicle does not exist." }
        require(!vehicle.isArchived) { "Archived vehicle cannot be current." }
        preferences.edit { it[CURRENT_VEHICLE_PUBLIC_ID] = publicId }
    }

    override suspend fun setVehicleArchived(id: Long, isArchived: Boolean) {
        val existing = requireNotNull(vehicleDao.getById(id)) { "Vehicle does not exist." }
        vehicleDao.update(
            existing.copy(
                isArchived = isArchived,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        reconcileCurrentVehicleSelection()
    }

    override suspend fun deleteUnreferencedVehicle(id: Long) {
        val existing = requireNotNull(vehicleDao.getById(id)) { "Vehicle does not exist." }
        require(fuelRecordDao.countForVehicle(id) == 0) { "Vehicle has fuel records." }
        require(serviceRecordDao.countForVehicle(id) == 0) { "Vehicle has service records." }
        vehicleDao.delete(existing)
        reconcileCurrentVehicleSelection()
    }

    private suspend fun reconcileCurrentVehicleSelection() {
        val vehicles = observeVehicles().first()
        preferences.edit { values ->
            val selected = resolveCurrentVehiclePublicId(
                vehicles = vehicles,
                storedPublicId = values[CURRENT_VEHICLE_PUBLIC_ID],
            )
            if (selected == null) {
                values.remove(CURRENT_VEHICLE_PUBLIC_ID)
            } else {
                values[CURRENT_VEHICLE_PUBLIC_ID] = selected
            }
        }
    }

    private companion object {
        val CURRENT_VEHICLE_PUBLIC_ID = stringPreferencesKey("current_vehicle_public_id")
    }
}

internal fun resolveCurrentVehiclePublicId(
    vehicles: List<Vehicle>,
    storedPublicId: String?,
): String? {
    val activeVehicles = vehicles.filterNot(Vehicle::isArchived)
    return storedPublicId
        ?.takeIf { selected -> activeVehicles.any { it.publicId == selected } }
        ?: activeVehicles.firstOrNull()?.publicId
}
