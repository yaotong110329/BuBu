package com.kumo.bubu.feature.history

import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.ServiceItem
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.feature.fuel.FakeFuelRepository
import com.kumo.bubu.feature.service.FakeServiceRepository
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun combinesAndFiltersThisVehiclesFuelAndMaintenanceRecords() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            records.value = listOf(fuelRecord(id = 1, date = 20_000), fuelRecord(id = 2, date = 20_002))
        }
        val serviceRepository = FakeServiceRepository().apply {
            historyDetails.value = listOf(serviceRecordDetails())
        }
        val viewModel = VehicleHistoryViewModel(
            vehicleId = 3,
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            fuelRepository = fuelRepository,
            serviceRepository = serviceRepository,
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.records.size)
        assertTrue(viewModel.uiState.value.records.first() is VehicleHistoryItem.Fuel)
        assertEquals(
            2,
            (viewModel.uiState.value.records.first() as VehicleHistoryItem.Fuel).record.id,
        )

        viewModel.onEvent(VehicleHistoryEvent.FilterChanged(HistoryFilter.MAINTENANCE))
        viewModel.onEvent(VehicleHistoryEvent.SearchChanged(" 機油 "))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.records.size)
        assertEquals("機油", (viewModel.uiState.value.records.single() as VehicleHistoryItem.Maintenance).primaryItemName)
    }

    @Test
    fun dateRangeCombinesWithTheExistingHistoryFilters() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            records.value = listOf(fuelRecord(id = 1, date = 20_000), fuelRecord(id = 2, date = 20_002))
        }
        val viewModel = VehicleHistoryViewModel(
            vehicleId = 3,
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            fuelRepository = fuelRepository,
            serviceRepository = FakeServiceRepository(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEvent(VehicleHistoryEvent.FilterChanged(HistoryFilter.FUEL))
        viewModel.onEvent(VehicleHistoryEvent.StartDateChanged("2024-10-06"))
        viewModel.onEvent(VehicleHistoryEvent.EndDateChanged("2024-10-06"))
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.uiState.value.records.map(VehicleHistoryItem::id))
    }

    @Test
    fun dateRangeAllowsOneOpenEndpointAndRejectsAnInvertedRange() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            records.value = listOf(fuelRecord(id = 1, date = 20_000), fuelRecord(id = 2, date = 20_002))
        }
        val viewModel = VehicleHistoryViewModel(
            vehicleId = 3,
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            fuelRepository = fuelRepository,
            serviceRepository = FakeServiceRepository(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEvent(VehicleHistoryEvent.StartDateChanged("2024-10-06"))
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.uiState.value.records.map(VehicleHistoryItem::id))
        assertTrue(!viewModel.uiState.value.hasInvalidDateRange)

        viewModel.onEvent(VehicleHistoryEvent.EndDateChanged("2024-10-05"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasInvalidDateRange)
    }

    private fun vehicle() = Vehicle(
        id = 3,
        publicId = "vehicle-public-id",
        name = "家用車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = "ABC-1234",
        powertrainType = null,
        trackingStartDateEpochDay = 19_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 1_500,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun fuelRecord(id: Long, date: Long) = FuelRecord(
        id = id,
        publicId = "fuel-$id",
        vehicleId = 3,
        dateEpochDay = date,
        timeMinuteOfDay = 600,
        sequenceInDay = 0,
        odometerKm = 1_000 + id * 100,
        fuelVolumeMl = 4_000,
        pricePerLiterMilli = 30_000,
        totalCostTwd = 120,
        isFullTank = true,
        fuelProduct = FuelProduct.GASOLINE_95,
        note = "自助加油",
        createdAt = 1,
        updatedAt = 1,
    )

    private fun serviceRecordDetails() = ServiceRecordDetails(
        record = ServiceRecord(
            id = 4,
            publicId = "service-4",
            vehicleId = 3,
            dateEpochDay = 20_001,
            timeMinuteOfDay = 600,
            sequenceInDay = 0,
            odometerKm = 1_250,
            recordType = ServiceRecordType.MAINTENANCE,
            title = "定期保養",
            paymentMethod = null,
            totalCostTwd = 800,
            note = "換機油",
            createdAt = 1,
            updatedAt = 1,
        ),
        items = listOf(
            ServiceItem(
                id = 5,
                publicId = "service-item-5",
                serviceRecordId = 4,
                serviceTypeId = null,
                sequenceInRecord = 0,
                nameSnapshot = "機油",
                quantityMilli = 1_000,
                quantityUnit = ServiceQuantityUnit.PIECE,
                unitPriceTwd = 800,
                subtotalTwd = 800,
                nextDueOdometerKm = null,
                nextDueDateEpochDay = null,
                note = null,
                createdAt = 1,
                updatedAt = 1,
            ),
        ),
    )
}
