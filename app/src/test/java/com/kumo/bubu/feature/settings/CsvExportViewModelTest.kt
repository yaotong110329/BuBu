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
    override suspend fun export(request: CsvExportRequest, destinationUriString: String): CsvExportResult =
        CsvExportResult("bubu-export-2026-08-21-000000.zip", 1)
}
