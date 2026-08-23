package com.kumo.bubu.feature.vehicle

import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleGarage
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeVehicleRepository(
    initialVehicles: List<Vehicle> = emptyList(),
) : VehicleRepository {
    val garage = MutableStateFlow(VehicleGarage(initialVehicles, initialVehicles.firstOrNull()?.publicId))
    val createdInputs = mutableListOf<VehicleInput>()
    val updatedInputs = mutableListOf<Pair<Long, VehicleInput>>()
    val selectedPublicIds = mutableListOf<String>()
    val archiveChanges = mutableListOf<Pair<Long, Boolean>>()
    val deletedIds = mutableListOf<Long>()

    override fun observeVehicles(): Flow<List<Vehicle>> = garage.map { it.vehicles }

    override fun observeGarage(): Flow<VehicleGarage> = garage

    override suspend fun getVehicle(id: Long): Vehicle? = garage.value.vehicles.firstOrNull { it.id == id }

    override suspend fun createVehicle(input: VehicleInput): Long {
        createdInputs += input
        return createdInputs.size.toLong()
    }

    override suspend fun updateVehicle(id: Long, input: VehicleInput) {
        updatedInputs += id to input
    }

    override suspend fun selectCurrentVehicle(publicId: String) {
        selectedPublicIds += publicId
    }

    override suspend fun setVehicleArchived(id: Long, isArchived: Boolean) {
        archiveChanges += id to isArchived
    }

    override suspend fun deleteUnreferencedVehicle(id: Long) {
        deletedIds += id
    }
}
