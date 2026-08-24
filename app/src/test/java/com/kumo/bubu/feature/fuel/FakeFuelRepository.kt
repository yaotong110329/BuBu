package com.kumo.bubu.feature.fuel

import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.FuelRecordInput
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import com.kumo.bubu.domain.repository.FuelOdometerNeighbors
import com.kumo.bubu.domain.repository.FuelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFuelRepository : FuelRepository {
    val records = MutableStateFlow<List<FuelRecord>>(emptyList())
    val createdInputs = mutableListOf<FuelRecordInput>()
    val updatedInputs = mutableListOf<Pair<Long, FuelRecordInput>>()
    val deletedIds = mutableListOf<Long>()
    val fuelEconomyStatisticsUpdates = mutableListOf<Pair<Long, FuelEconomyStatisticsStatus>>()
    val fullTankSettings = mutableMapOf<String, Boolean>()
    val lastFuelProducts = mutableMapOf<String, FuelProduct>()
    val lastFuelingModes = mutableMapOf<String, FuelingMode>()
    var lastPriceForProduct: Long? = null
    var odometerNeighbors = FuelOdometerNeighbors(previous = null, next = null)

    override fun observeRecentFuelRecords(): Flow<List<FuelRecord>> = records

    override fun observeFuelRecords(vehicleId: Long): Flow<List<FuelRecord>> = records

    override suspend fun getFuelRecord(id: Long): FuelRecord? = records.value.firstOrNull { it.id == id }

    override suspend fun getOdometerNeighbors(
        input: FuelRecordInput,
        editingRecordId: Long?,
    ): FuelOdometerNeighbors = odometerNeighbors

    override suspend fun getLastFullTankSetting(vehiclePublicId: String): Boolean? = fullTankSettings[vehiclePublicId]

    override suspend fun getLastFuelProduct(vehiclePublicId: String): FuelProduct? = lastFuelProducts[vehiclePublicId]

    override suspend fun getLastFuelingMode(vehiclePublicId: String): FuelingMode? = lastFuelingModes[vehiclePublicId]

    override suspend fun getLastPriceForProduct(
        vehicleId: Long,
        fuelProduct: FuelProduct,
        dateEpochDay: Long,
    ): Long? = lastPriceForProduct

    override suspend fun createFuelRecord(input: FuelRecordInput): Long {
        createdInputs += input
        return createdInputs.size.toLong()
    }

    override suspend fun updateFuelRecord(id: Long, input: FuelRecordInput) {
        updatedInputs += id to input
    }

    override suspend fun setFuelEconomyStatisticsStatus(id: Long, status: FuelEconomyStatisticsStatus) {
        fuelEconomyStatisticsUpdates += id to status
    }

    override suspend fun deleteFuelRecord(id: Long) {
        deletedIds += id
    }
}
