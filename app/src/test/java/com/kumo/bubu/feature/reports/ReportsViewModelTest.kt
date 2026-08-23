package com.kumo.bubu.feature.reports

import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.ReportData
import com.kumo.bubu.domain.model.ReportQuery
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.repository.ReportRepository
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fuelTrendAndQueryUseTheSelectedPeriodInsteadOfBackfillingOlderPoints() = runTest {
        val reportRepository = FakeReportRepository(
            ReportData(
                fuelRecords = listOf(
                    fuelRecord(1, LocalDate.of(2025, 12, 1), 1_000),
                    fuelRecord(2, LocalDate.of(2026, 2, 1), 1_100),
                    fuelRecord(3, LocalDate.of(2026, 3, 1), 1_200),
                    fuelRecord(4, LocalDate.of(2026, 8, 1), 1_300),
                ),
            ),
        )
        val viewModel = ReportsViewModel(
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            reportRepository = reportRepository,
            todayProvider = { LocalDate.of(2026, 8, 23) },
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 1)),
            viewModel.uiState.value.report?.fuelEconomy?.points?.map { LocalDate.ofEpochDay(it.dateEpochDay) },
        )
        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), reportRepository.queries.last().startEpochDay)

        viewModel.onEvent(ReportsEvent.SelectPeriod(ReportPeriod.LAST_1_YEAR))
        advanceUntilIdle()

        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 8, 1),
            ),
            viewModel.uiState.value.report?.fuelEconomy?.points?.map { LocalDate.ofEpochDay(it.dateEpochDay) },
        )
        assertEquals(LocalDate.of(2025, 9, 1).toEpochDay(), reportRepository.queries.last().startEpochDay)
    }

    @Test
    fun fuelTrendKeepsEveryValidPointInsideTheSelectedPeriod() = runTest {
        val start = LocalDate.of(2025, 9, 1)
        val reportRepository = FakeReportRepository(
            ReportData(
                fuelRecords = (0L..9L).map { offset ->
                    fuelRecord(
                        id = offset + 1,
                        date = start.plusMonths(offset),
                        odometerKm = 1_000 + offset * 100,
                    )
                },
            ),
        )
        val viewModel = ReportsViewModel(
            vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
            reportRepository = reportRepository,
            todayProvider = { LocalDate.of(2026, 8, 23) },
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onEvent(ReportsEvent.SelectPeriod(ReportPeriod.LAST_1_YEAR))
        advanceUntilIdle()

        assertEquals(
            (1L..9L).map { start.plusMonths(it) },
            viewModel.uiState.value.report?.fuelEconomy?.points?.map { LocalDate.ofEpochDay(it.dateEpochDay) },
        )
    }

    private fun vehicle() = Vehicle(
        id = 1,
        publicId = "vehicle-1",
        name = "測試汽車",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = LocalDate.of(2025, 1, 1).toEpochDay(),
        trackingStartOdometerKm = 900,
        currentOdometerKm = 1_300,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun fuelRecord(id: Long, date: LocalDate, odometerKm: Long) = FuelRecord(
        id = id,
        publicId = "fuel-$id",
        vehicleId = 1,
        dateEpochDay = date.toEpochDay(),
        timeMinuteOfDay = null,
        sequenceInDay = 0,
        odometerKm = odometerKm,
        fuelVolumeMl = 5_000,
        pricePerLiterMilli = null,
        totalCostTwd = 150,
        isFullTank = true,
        fuelProduct = FuelProduct.GASOLINE_95,
        note = null,
        createdAt = 1,
        updatedAt = 1,
    )
}

private class FakeReportRepository(data: ReportData) : ReportRepository {
    private val reports = MutableStateFlow(data)
    val queries = mutableListOf<ReportQuery>()

    override fun observeReport(query: ReportQuery): StateFlow<ReportData> {
        queries += query
        return reports.asStateFlow()
    }
}
