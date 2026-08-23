package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleGarage
import com.kumo.bubu.domain.model.VehicleInput
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeVehicles(): Flow<List<Vehicle>>

    fun observeGarage(): Flow<VehicleGarage>

    suspend fun getVehicle(id: Long): Vehicle?

    suspend fun createVehicle(input: VehicleInput): Long

    suspend fun updateVehicle(id: Long, input: VehicleInput)

    suspend fun selectCurrentVehicle(publicId: String)

    suspend fun setVehicleArchived(id: Long, isArchived: Boolean)

    suspend fun deleteUnreferencedVehicle(id: Long)
}
