package com.kumo.bubu.feature.service

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.repository.OfflineServiceRepository
import com.kumo.bubu.domain.model.BuiltInServiceTypeSeed
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleGarage
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.VehicleRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceFormViewModelIntegrationTest {
    private lateinit var database: BuBuDatabase
    private lateinit var repository: OfflineServiceRepository
    private lateinit var vehicle: Vehicle

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BuBuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val vehicleId = database.vehicleDao().insert(vehicleEntity())
        vehicle = vehicle(vehicleId)
        repository = OfflineServiceRepository(
            database = database,
            vehicleDao = database.vehicleDao(),
            fuelRecordDao = database.fuelRecordDao(),
            serviceRecordDao = database.serviceRecordDao(),
            serviceItemDao = database.serviceItemDao(),
            serviceTypeDao = database.serviceTypeDao(),
            builtInServiceTypeSeeds = listOf(BuiltInServiceTypeSeed("engine-oil", "機油")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rapidSaveTapsPersistExactlyOneWorkOrderInRoom() = runBlocking {
        val viewModel = ServiceFormViewModel(
            serviceRepository = repository,
            vehicleRepository = SingleVehicleRepository(vehicle),
            nowProvider = { LocalDateTime.of(LocalDate.now(), LocalTime.NOON) },
        )
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        viewModel.onEvent(ServiceFormEvent.TitleChanged("定期保養"))
        viewModel.onEvent(ServiceFormEvent.OdometerChanged("12345"))
        viewModel.onEvent(ServiceFormEvent.CustomItemNameChanged("機油"))
        viewModel.onEvent(ServiceFormEvent.AddCustomItem)
        val itemKey = requireNotNull(viewModel.uiState.value.itemEditorDraft).draftKey
        viewModel.onEvent(ServiceFormEvent.ItemAmountChanged(itemKey, "800"))
        viewModel.onEvent(ServiceFormEvent.CompleteItemEditor)

        viewModel.onEvent(ServiceFormEvent.Save)
        viewModel.onEvent(ServiceFormEvent.Save)

        withTimeout(5_000) {
            repository.observeRecentServiceRecords().first { records -> records.size == 1 }
        }
        delay(250)
        assertEquals(1, repository.observeRecentServiceRecords().first().size)
        viewModel.viewModelScope.cancel()
    }

    private fun vehicleEntity() = com.kumo.bubu.data.local.entity.VehicleEntity(
        publicId = "rapid-save-vehicle",
        name = "測試車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 19_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun vehicle(id: Long) = Vehicle(
        id = id,
        publicId = "rapid-save-vehicle",
        name = "測試車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 19_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )
}

private class SingleVehicleRepository(vehicle: Vehicle) : VehicleRepository {
    private val garage = MutableStateFlow(VehicleGarage(listOf(vehicle), vehicle.publicId))

    override fun observeVehicles(): Flow<List<Vehicle>> = garage.map { state -> state.vehicles }

    override fun observeGarage(): Flow<VehicleGarage> = garage

    override suspend fun getVehicle(id: Long): Vehicle? =
        garage.value.vehicles.firstOrNull { vehicle -> vehicle.id == id }

    override suspend fun createVehicle(input: VehicleInput): Long = error("Not used by this test.")

    override suspend fun updateVehicle(id: Long, input: VehicleInput) = error("Not used by this test.")

    override suspend fun selectCurrentVehicle(publicId: String) = Unit

    override suspend fun setVehicleArchived(id: Long, isArchived: Boolean) =
        error("Not used by this test.")

    override suspend fun deleteUnreferencedVehicle(id: Long) = error("Not used by this test.")
}
