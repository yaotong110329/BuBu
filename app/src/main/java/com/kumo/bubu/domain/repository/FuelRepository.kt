package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.FuelRecordInput
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import kotlinx.coroutines.flow.Flow

data class FuelOdometerNeighbors(
    val previous: FuelRecord?,
    val next: FuelRecord?,
) {
    fun breaksOrder(odometerKm: Long): Boolean =
        (previous != null && odometerKm < previous.odometerKm) ||
            (next != null && odometerKm > next.odometerKm)
}

interface FuelRepository {
    fun observeRecentFuelRecords(): Flow<List<FuelRecord>>

    fun observeFuelRecords(vehicleId: Long): Flow<List<FuelRecord>>

    suspend fun getFuelRecord(id: Long): FuelRecord?

    suspend fun getOdometerNeighbors(
        input: FuelRecordInput,
        editingRecordId: Long?,
    ): FuelOdometerNeighbors

    suspend fun getLastFullTankSetting(vehiclePublicId: String): Boolean?

    suspend fun getLastFuelProduct(vehiclePublicId: String): FuelProduct?

    suspend fun getLastFuelingMode(vehiclePublicId: String): FuelingMode?

    suspend fun getLastPriceForProduct(vehicleId: Long, fuelProduct: FuelProduct, dateEpochDay: Long): Long?

    suspend fun createFuelRecord(input: FuelRecordInput): Long

    suspend fun updateFuelRecord(id: Long, input: FuelRecordInput)

    suspend fun setFuelEconomyStatisticsStatus(id: Long, status: FuelEconomyStatisticsStatus)

    suspend fun deleteFuelRecord(id: Long)
}
