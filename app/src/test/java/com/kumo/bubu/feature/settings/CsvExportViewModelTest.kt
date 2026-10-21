package com.kumo.bubu.feature.settings

import com.kumo.bubu.domain.model.CsvExportRequest
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.CsvExportRepository
import com.kumo.bubu.domain.repository.CsvExportResult
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CsvExportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun buildsARequestForSelectedVehicleAndLocalDateRange() = runTest {
        val viewModel = CsvExportViewModel(FakeVehicleRepository(listOf(vehicle(7))), FakeCsvExportRepository())
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEvent(CsvExportEvent.SetIncludeAllVehicles(false))
        viewModel.onEvent(CsvExportEvent.ToggleVehicle(7))
        viewModel.onEvent(CsvExportEvent.ChangeStartDate("2026-08-01"))
        viewModel.onEvent(CsvExportEvent.ChangeEndDate("2026-08-31"))

        assertEquals(
            CsvExportRequest(
                vehicleIds = setOf(7),
                startEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                endEpochDay = LocalDate.of(2026, 8, 31).toEpochDay(),
            ),
            viewModel.createRequest(),
        )
    }

    @Test
    fun rejectsAnInvalidDateRangeBeforeOpeningAUserDestination() = runTest {
        val viewModel = CsvExportViewModel(FakeVehicleRepository(listOf(vehicle(7))), FakeCsvExportRepository())
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEvent(CsvExportEvent.ChangeStartDate("2026-08-31"))
        viewModel.onEvent(CsvExportEvent.ChangeEndDate("2026-08-01"))

        assertNull(viewModel.createRequest())
        advanceUntilIdle()
        assertSame(CsvExportError.INVALID_DATE_RANGE, viewModel.uiState.value.error)
    }

    @Test
    fun destinationUsesOriginalRequestOnceAndRejectsAnOldResultAfterRetry() = runTest {
        val repository = FakeCsvExportRepository()
        val viewModel = CsvExportViewModel(FakeVehicleRepository(listOf(vehicle(7))), repository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertEquals(true, viewModel.prepareDestination())
        val firstId = requireNotNull(viewModel.destinationRequestId.value)
        assertEquals(true, viewModel.consumeDestinationLaunch(firstId))
        assertEquals(false, viewModel.consumeDestinationLaunch(firstId))
        viewModel.onDestinationResult(firstId, null)
        assertNull(viewModel.destinationRequestId.value)
        assertEquals(true, viewModel.prepareDestination())
        val retryId = requireNotNull(viewModel.destinationRequestId.value)
        viewModel.onEvent(CsvExportEvent.ChangeStartDate("2026-08-01"))
        viewModel.onDestinationResult(firstId, "old-destination")
        viewModel.onDestinationResult(retryId, "new-destination")
        viewModel.onDestinationResult(retryId, "duplicate")
        advanceUntilIdle()
        assertEquals(listOf("new-destination"), repository.destinations)
        assertEquals(listOf(CsvExportRequest(setOf(7), null, null)), repository.requests)
    }

    @Test
    fun leavingInvalidatesAnUnconfirmedDestinationAndLaunchFailureAllowsRetry() = runTest {
        val repository = FakeCsvExportRepository()
        val viewModel = CsvExportViewModel(FakeVehicleRepository(listOf(vehicle(7))), repository)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.prepareDestination()
        val abandonedId = requireNotNull(viewModel.destinationRequestId.value)
        viewModel.cancelDestination()
        viewModel.onDestinationResult(abandonedId, "abandoned")
        viewModel.prepareDestination()
        viewModel.onDestinationLaunchFailed(requireNotNull(viewModel.destinationRequestId.value))
        advanceUntilIdle()
        assertSame(CsvExportError.WRITE_FAILED, viewModel.uiState.value.error)
        assertEquals(true, viewModel.prepareDestination())
        assertEquals(emptyList<String>(), repository.destinations)
    }

    private fun vehicle(id: Long) = Vehicle(
        id = id,
        publicId = "vehicle-$id",
        name = "RAV4",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 0,
        trackingStartOdometerKm = 0,
        currentOdometerKm = 0,
        note = null,
        isArchived = false,
        createdAt = 0,
        updatedAt = 0,
    )
}

private class FakeCsvExportRepository : CsvExportRepository {
    val destinations = mutableListOf<String>()
    val requests = mutableListOf<CsvExportRequest>()
    override suspend fun export(request: CsvExportRequest, destinationUriString: String): CsvExportResult {
        destinations += destinationUriString
        requests += request
        return CsvExportResult("bubu-export-2026-08-21-000000.zip", 1)
    }
}
