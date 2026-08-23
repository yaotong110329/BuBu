package com.kumo.bubu.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.local.dao.FuelRecordDao
import com.kumo.bubu.data.local.dao.ServiceRecordDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.data.mapper.toNewEntity
import com.kumo.bubu.data.mapper.toUpdatedEntity
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.FuelRecordInput
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.validated
import com.kumo.bubu.domain.repository.FuelOdometerNeighbors
import com.kumo.bubu.domain.repository.FuelRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineFuelRepository(
    private val database: BuBuDatabase,
    private val vehicleDao: VehicleDao,
    private val fuelRecordDao: FuelRecordDao,
    private val serviceRecordDao: ServiceRecordDao,
    private val preferences: DataStore<Preferences>,
) : FuelRepository {
    override fun observeRecentFuelRecords(): Flow<List<FuelRecord>> =
        fuelRecordDao.observeRecent().map { records -> records.map { it.toDomain() } }

    override fun observeFuelRecords(vehicleId: Long): Flow<List<FuelRecord>> =
        fuelRecordDao.observeForVehicle(vehicleId).map { records -> records.map { it.toDomain() } }

    override suspend fun getFuelRecord(id: Long): FuelRecord? = fuelRecordDao.getById(id)?.toDomain()

    override suspend fun getOdometerNeighbors(
        input: FuelRecordInput,
        editingRecordId: Long?,
    ): FuelOdometerNeighbors {
        val existing = editingRecordId?.let { fuelRecordDao.getById(it) }
        val sequence = if (
            existing != null &&
            existing.vehicleId == input.vehicleId &&
            existing.dateEpochDay == input.dateEpochDay
        ) {
            existing.sequenceInDay
        } else {
            fuelRecordDao.nextSequenceInDay(input.vehicleId, input.dateEpochDay)
        }
        val candidate = FuelRecordOrderKey(
            input.dateEpochDay,
            input.timeMinuteOfDay ?: UNKNOWN_TIME_ORDER,
            sequence,
            editingRecordId ?: Long.MAX_VALUE,
        )
        val records = fuelRecordDao.getForVehicleInRecordOrder(input.vehicleId)
            .asSequence()
            .filterNot { it.id == editingRecordId }
            .map { it.toDomain() }
            .toList()
        return FuelOdometerNeighbors(
            previous = records.lastOrNull { it.orderKey() < candidate },
            next = records.firstOrNull { it.orderKey() > candidate },
        )
    }

    override suspend fun getLastFullTankSetting(vehiclePublicId: String): Boolean? =
        preferences.data.map { values -> values[fullTankKey(vehiclePublicId)] }.first()

    override suspend fun getLastFuelProduct(vehiclePublicId: String): FuelProduct? = preferences.data
        .map { values -> values[lastFuelProductKey(vehiclePublicId)]?.let(FuelProduct::valueOf) }
        .first()

    override suspend fun getLastFuelingMode(vehiclePublicId: String): FuelingMode? = preferences.data
        .map { values -> values[lastFuelingModeKey(vehiclePublicId)]?.let { runCatching { FuelingMode.valueOf(it) }.getOrNull() } }
        .first()

    override suspend fun getLastPriceForProduct(
        vehicleId: Long,
        fuelProduct: FuelProduct,
        dateEpochDay: Long,
    ): Long? = fuelRecordDao.getLastPriceForProduct(vehicleId, fuelProduct, dateEpochDay)

    override suspend fun createFuelRecord(input: FuelRecordInput): Long {
        val result = database.withTransaction {
            val vehicle = requireNotNull(vehicleDao.getById(input.vehicleId)) { "Vehicle does not exist." }
            require(!vehicle.isArchived) { "Archived vehicle cannot receive fuel records." }
            val validInput = input.validated()
            val now = System.currentTimeMillis()
            val id = fuelRecordDao.insert(
                validInput.toNewEntity(
                    publicId = UUID.randomUUID().toString(),
                    sequenceInDay = fuelRecordDao.nextSequenceInDay(validInput.vehicleId, validInput.dateEpochDay),
                    nowEpochMillis = now,
                ),
            )
            rebuildVehicleCurrentOdometer(vehicle, now)
            id to vehicle.publicId
        }
        preferences.edit { values ->
            values[fullTankKey(result.second)] = input.isFullTank
            input.fuelProduct?.let { values[lastFuelProductKey(result.second)] = it.name }
            values[lastFuelingModeKey(result.second)] = input.fuelingMode.name
        }
        return result.first
    }

    override suspend fun updateFuelRecord(id: Long, input: FuelRecordInput) {
        val publicId = database.withTransaction {
            val existing = requireNotNull(fuelRecordDao.getById(id)) { "Fuel record does not exist." }
            val validInput = input.validated()
            val targetVehicle = requireNotNull(vehicleDao.getById(validInput.vehicleId)) { "Vehicle does not exist." }
            require(!targetVehicle.isArchived || targetVehicle.id == existing.vehicleId) {
                "Archived vehicle cannot receive moved fuel records."
            }
            val originalVehicle = requireNotNull(vehicleDao.getById(existing.vehicleId)) { "Vehicle does not exist." }
            val now = System.currentTimeMillis()
            val sequence = if (existing.dateEpochDay == validInput.dateEpochDay && existing.vehicleId == validInput.vehicleId) {
                existing.sequenceInDay
            } else {
                fuelRecordDao.nextSequenceInDay(validInput.vehicleId, validInput.dateEpochDay)
            }
            fuelRecordDao.update(validInput.toUpdatedEntity(existing, sequence, now))
            rebuildVehicleCurrentOdometer(originalVehicle, now)
            if (targetVehicle.id != originalVehicle.id) rebuildVehicleCurrentOdometer(targetVehicle, now)
            targetVehicle.publicId
        }
        preferences.edit { values ->
            values[fullTankKey(publicId)] = input.isFullTank
            input.fuelProduct?.let { values[lastFuelProductKey(publicId)] = it.name }
            values[lastFuelingModeKey(publicId)] = input.fuelingMode.name
        }
    }

    override suspend fun deleteFuelRecord(id: Long) {
        database.withTransaction {
            val existing = requireNotNull(fuelRecordDao.getById(id)) { "Fuel record does not exist." }
            val vehicle = requireNotNull(vehicleDao.getById(existing.vehicleId)) { "Vehicle does not exist." }
            val now = System.currentTimeMillis()
            fuelRecordDao.deleteById(id)
            rebuildVehicleCurrentOdometer(vehicle, now)
        }
    }

    private suspend fun rebuildVehicleCurrentOdometer(
        vehicle: com.kumo.bubu.data.local.entity.VehicleEntity,
        nowEpochMillis: Long,
    ) {
        val currentOdometer = maxOf(
            vehicle.trackingStartOdometerKm,
            fuelRecordDao.maxOdometerKm(vehicle.id) ?: vehicle.trackingStartOdometerKm,
            serviceRecordDao.maxOdometerKm(vehicle.id) ?: vehicle.trackingStartOdometerKm,
        )
        vehicleDao.updateCurrentOdometer(vehicle.id, currentOdometer, nowEpochMillis)
    }

    private fun fullTankKey(vehiclePublicId: String) = booleanPreferencesKey("fuel_full_tank_$vehiclePublicId")
    private fun lastFuelProductKey(vehiclePublicId: String) = stringPreferencesKey("fuel_product_$vehiclePublicId")
    private fun lastFuelingModeKey(vehiclePublicId: String) = stringPreferencesKey("fuel_mode_$vehiclePublicId")
}

private data class FuelRecordOrderKey(
    val dateEpochDay: Long,
    val timeOrder: Int,
    val sequenceInDay: Int,
    val id: Long,
) : Comparable<FuelRecordOrderKey> {
    override fun compareTo(other: FuelRecordOrderKey): Int = compareValuesBy(
        this,
        other,
        FuelRecordOrderKey::dateEpochDay,
        FuelRecordOrderKey::timeOrder,
        FuelRecordOrderKey::sequenceInDay,
        FuelRecordOrderKey::id,
    )
}

private fun FuelRecord.orderKey() = FuelRecordOrderKey(
    dateEpochDay,
    timeMinuteOfDay ?: UNKNOWN_TIME_ORDER,
    sequenceInDay,
    id,
)

private const val UNKNOWN_TIME_ORDER = -1
