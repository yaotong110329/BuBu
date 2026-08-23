package com.kumo.bubu.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ReportCostCategory
import com.kumo.bubu.domain.model.ReportData
import com.kumo.bubu.domain.model.ReportFuelEconomyPoint
import com.kumo.bubu.domain.model.ReportMonthTotal
import com.kumo.bubu.domain.model.ReportOdometerRecord
import com.kumo.bubu.domain.model.ReportQuery
import com.kumo.bubu.domain.model.ReportLayout
import com.kumo.bubu.domain.model.ReportCard
import com.kumo.bubu.domain.model.ReportSource
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleGarage
import com.kumo.bubu.domain.model.calculateReportFuelEconomyPoints
import com.kumo.bubu.domain.model.calculateWeightedFuelEconomyMilliKmPerLiter
import com.kumo.bubu.domain.repository.ReportRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import com.kumo.bubu.domain.repository.ReportLayoutSettings
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReportPeriod {
    LAST_6_MONTHS,
    LAST_1_YEAR,
    LAST_2_YEARS,
    ALL,
}

data class ReportsUiState(
    val vehicles: List<ReportVehicleOption> = emptyList(),
    val selectedVehicleId: Long? = null,
    val period: ReportPeriod = ReportPeriod.LAST_6_MONTHS,
    val layout: ReportLayout = ReportLayout(),
    val isLayoutEditorVisible: Boolean = false,
    val report: ReportPresentation? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

data class ReportVehicleOption(
    val id: Long,
    val name: String,
    val licensePlate: String?,
)

data class ReportPresentation(
    val hasData: Boolean,
    val totalCostTwd: Long,
    val monthlyAverageCostTwd: Long,
    val categoryTotals: Map<ReportCostCategory, Long>,
    val monthlyTotals: List<ReportMonthUi>,
    val serviceMonthlyTotals: List<ReportServiceMonthUi>,
    val fuelEconomy: ReportFuelEconomyUi,
    val costPerKm: ReportCostPerKmUi,
    val mileage: ReportMileageUi,
)

data class ReportMonthUi(
    val monthKey: String,
    val totalCostTwd: Long,
    val fuelCostTwd: Long,
    val serviceCostTwd: Long,
)

data class ReportServiceMonthUi(
    val monthKey: String,
    val maintenanceCostTwd: Long,
    val repairCostTwd: Long,
)

data class ReportFuelEconomyUi(
    val vehicleName: String,
    val averageMilliKmPerLiter: Long?,
    val points: List<ReportFuelEconomyPoint>,
    val trendStartEpochDay: Long,
    val trendEndEpochDay: Long,
)

data class ReportCostPerKmUi(
    val vehicleName: String,
    val milliTwdPerKm: Long?,
    val distanceKm: Long?,
)

data class ReportMileageUi(
    val vehicleName: String,
    val points: List<ReportMileagePoint>,
)

data class ReportMileagePoint(
    val dateEpochDay: Long,
    val odometerKm: Long,
    val source: ReportSource,
)

sealed interface ReportsEvent {
    data class SelectVehicle(val vehicleId: Long) : ReportsEvent
    data class SelectPeriod(val period: ReportPeriod) : ReportsEvent
    data object OpenLayoutEditor : ReportsEvent
    data object CloseLayoutEditor : ReportsEvent
    data class MoveCard(val card: ReportCard, val offset: Int) : ReportsEvent
    data class SetCardVisible(val card: ReportCard, val visible: Boolean) : ReportsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    vehicleRepository: VehicleRepository,
    private val reportRepository: ReportRepository,
    private val reportLayoutSettings: ReportLayoutSettings = DefaultReportLayoutSettings,
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val controls = MutableStateFlow(ReportControls())

    val uiState = combine(vehicleRepository.observeGarage(), controls, reportLayoutSettings.observeLayout()) { garage, reportControls, layout ->
        ReportInputs(garage, reportControls.resolve(garage), layout)
    }
        .flatMapLatest { inputs ->
            val controls = inputs.controls
            val dateRange = controls.toDateRange(todayProvider())
            val selectedVehicle = inputs.garage.vehicles.firstOrNull { it.id == controls.selectedVehicleId }
            if (selectedVehicle == null || dateRange == null) {
                flowOf(
                    ReportsUiState(
                        vehicles = inputs.garage.vehicles.filterNot(Vehicle::isArchived).map(Vehicle::toReportOption),
                        selectedVehicleId = controls.selectedVehicleId,
                        period = controls.period,
                        layout = inputs.layout,
                        isLayoutEditorVisible = controls.isLayoutEditorVisible,
                        isLoading = false,
                    ),
                )
            } else {
                reportRepository.observeReport(
                    ReportQuery(
                        vehicleIds = listOf(selectedVehicle.id),
                        startEpochDay = dateRange.startEpochDay,
                        endEpochDay = dateRange.endEpochDay,
                    ),
                ).map { data ->
                    ReportsUiState(
                        vehicles = inputs.garage.vehicles.filterNot(Vehicle::isArchived).map(Vehicle::toReportOption),
                        selectedVehicleId = selectedVehicle.id,
                        period = controls.period,
                        layout = inputs.layout,
                        isLayoutEditorVisible = controls.isLayoutEditorVisible,
                        report = data.toPresentation(selectedVehicle, dateRange),
                        isLoading = false,
                    )
                }
            }
        }
        .catch { emit(ReportsUiState(isLoading = false, loadFailed = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(),
        )

    fun onEvent(event: ReportsEvent) {
        controls.value = when (event) {
            is ReportsEvent.SelectVehicle -> controls.value.copy(selectedVehicleId = event.vehicleId)
            is ReportsEvent.SelectPeriod -> controls.value.copy(period = event.period)
            ReportsEvent.OpenLayoutEditor -> controls.value.copy(isLayoutEditorVisible = true)
            ReportsEvent.CloseLayoutEditor -> controls.value.copy(isLayoutEditorVisible = false)
            is ReportsEvent.MoveCard -> {
                saveLayout(uiState.value.layout.move(event.card, event.offset))
                controls.value
            }
            is ReportsEvent.SetCardVisible -> {
                saveLayout(uiState.value.layout.setVisible(event.card, event.visible))
                controls.value
            }
        }
    }

    private fun saveLayout(layout: ReportLayout) {
        viewModelScope.launch { reportLayoutSettings.saveLayout(layout) }
    }

    companion object {
        fun factory(
            vehicleRepository: VehicleRepository,
            reportRepository: ReportRepository,
            reportLayoutSettings: ReportLayoutSettings = DefaultReportLayoutSettings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportsViewModel(vehicleRepository, reportRepository, reportLayoutSettings) }
        }
    }
}

private data class ReportInputs(
    val garage: VehicleGarage,
    val controls: ReportControls,
    val layout: ReportLayout,
)

private data class ReportControls(
    val selectedVehicleId: Long? = null,
    val period: ReportPeriod = ReportPeriod.LAST_6_MONTHS,
    val isLayoutEditorVisible: Boolean = false,
) {
    fun resolve(garage: VehicleGarage): ReportControls {
        val activeVehicles = garage.vehicles.filterNot(Vehicle::isArchived)
        val resolvedVehicleId = selectedVehicleId?.takeIf { selectedId -> activeVehicles.any { it.id == selectedId } }
            ?: activeVehicles.firstOrNull { it.publicId == garage.currentVehiclePublicId }?.id
            ?: activeVehicles.firstOrNull()?.id
        return copy(selectedVehicleId = resolvedVehicleId)
    }

    fun toDateRange(today: LocalDate): ReportDateRange? = when (period) {
        ReportPeriod.LAST_6_MONTHS -> ReportDateRange(today.minusMonths(5).withDayOfMonth(1), today)
        ReportPeriod.LAST_1_YEAR -> ReportDateRange(today.minusMonths(11).withDayOfMonth(1), today)
        ReportPeriod.LAST_2_YEARS -> ReportDateRange(today.minusMonths(23).withDayOfMonth(1), today)
        ReportPeriod.ALL -> ReportDateRange(LocalDate.of(1, 1, 1), today)
    }
}

private object DefaultReportLayoutSettings : ReportLayoutSettings {
    override fun observeLayout() = flowOf(ReportLayout())

    override suspend fun saveLayout(layout: ReportLayout) = Unit
}

private data class ReportDateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!start.isAfter(end))
    }

    val startEpochDay: Long get() = start.toEpochDay()
    val endEpochDay: Long get() = end.toEpochDay()
    val monthCount: Long get() = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1
}

private fun Vehicle.toReportOption() = ReportVehicleOption(id, name, licensePlate)

private fun ReportData.toPresentation(
    vehicle: Vehicle,
    dateRange: ReportDateRange,
): ReportPresentation {
    val categoryTotals = ReportCostCategory.entries.associateWith { category ->
        categoryTotals.firstOrNull { it.category == category }?.totalCostTwd ?: 0L
    }
    val totalCost = categoryTotals.values.sumCostsOrZero()
    val fuelPoints = calculateReportFuelEconomyPoints(fuelRecords)
        .filter { it.vehicleId == vehicle.id && it.dateEpochDay in dateRange.startEpochDay..dateRange.endEpochDay }
    val distance = observedDistanceKm(vehicle, odometerRecords, dateRange.startEpochDay, dateRange.endEpochDay)
    val mileagePoints = odometerRecords
        .filter { it.vehicleId == vehicle.id && it.dateEpochDay in dateRange.startEpochDay..dateRange.endEpochDay }
        .sortedWith(REPORT_ODOMETER_ORDER)
        .map { ReportMileagePoint(it.dateEpochDay, it.odometerKm, it.source) }
    return ReportPresentation(
        hasData = totalCost != 0L || fuelRecords.any { it.dateEpochDay in dateRange.startEpochDay..dateRange.endEpochDay } || mileagePoints.isNotEmpty(),
        totalCostTwd = totalCost,
        monthlyAverageCostTwd = totalCost.divideRounded(
            if (dateRange.start.year == 1) monthlyTotals.size.coerceAtLeast(1).toLong() else dateRange.monthCount,
        ),
        categoryTotals = categoryTotals,
        monthlyTotals = dateRange.monthKeys(monthlyTotals.map(ReportMonthTotal::monthKey)).map { monthKey ->
            val month = monthlyTotals.firstOrNull { it.monthKey == monthKey }
            val monthCategories = monthlyCategoryTotals.filter { it.monthKey == monthKey }
            ReportMonthUi(
                monthKey = monthKey,
                totalCostTwd = month?.totalCostTwd ?: 0L,
                fuelCostTwd = monthCategories.firstOrNull { it.category == ReportCostCategory.FUEL }?.totalCostTwd ?: 0L,
                serviceCostTwd = monthCategories
                    .filter { it.category == ReportCostCategory.MAINTENANCE || it.category == ReportCostCategory.REPAIR }
                    .map { it.totalCostTwd }
                    .sumCostsOrZero(),
            )
        },
        serviceMonthlyTotals = serviceMonthlyTotals
            .groupBy { it.monthKey }
            .map { (monthKey, rows) ->
                ReportServiceMonthUi(
                    monthKey = monthKey,
                    maintenanceCostTwd = rows.firstOrNull { it.recordType == ServiceRecordType.MAINTENANCE }?.totalCostTwd ?: 0L,
                    repairCostTwd = rows.firstOrNull { it.recordType == ServiceRecordType.REPAIR }?.totalCostTwd ?: 0L,
                )
            },
        fuelEconomy = ReportFuelEconomyUi(
            vehicleName = vehicle.name,
            averageMilliKmPerLiter = calculateWeightedFuelEconomyMilliKmPerLiter(fuelPoints),
            points = fuelPoints,
            trendStartEpochDay = if (dateRange.start.year == 1) {
                fuelPoints.firstOrNull()?.dateEpochDay ?: dateRange.endEpochDay
            } else {
                dateRange.startEpochDay
            },
            trendEndEpochDay = dateRange.endEpochDay,
        ),
        costPerKm = ReportCostPerKmUi(
            vehicleName = vehicle.name,
            milliTwdPerKm = distance?.let { totalCost.toMilliPerKm(it) },
            distanceKm = distance,
        ),
        mileage = ReportMileageUi(vehicle.name, mileagePoints),
    )
}

private fun observedDistanceKm(
    vehicle: Vehicle,
    records: List<ReportOdometerRecord>,
    startEpochDay: Long,
    endEpochDay: Long,
): Long? {
    val vehicleRecords = records.filter { it.vehicleId == vehicle.id }.sortedWith(REPORT_ODOMETER_ORDER)
    val start = vehicleRecords.lastOrNull { it.dateEpochDay < startEpochDay }?.odometerKm
        ?: vehicle.trackingStartOdometerKm
    val end = vehicleRecords.lastOrNull { it.dateEpochDay <= endEpochDay }?.odometerKm ?: return null
    return (end - start).takeIf { it > 0 }
}

private val REPORT_ODOMETER_ORDER = compareBy<ReportOdometerRecord>(ReportOdometerRecord::dateEpochDay)
    .thenBy { it.timeMinuteOfDay ?: Int.MAX_VALUE }
    .thenBy(ReportOdometerRecord::sequenceInDay)

private fun Iterable<Long>.sumCostsOrZero(): Long = runCatching {
    fold(0L) { total, amount -> Math.addExact(total, amount) }
}.getOrDefault(0L)

private fun Long.divideRounded(divisor: Long): Long = BigDecimal.valueOf(this)
    .divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP)
    .longValueExact()

private fun Long.toMilliPerKm(distanceKm: Long): Long? = runCatching {
    BigDecimal.valueOf(this)
        .multiply(BigDecimal.valueOf(1_000L))
        .divide(BigDecimal.valueOf(distanceKm), 0, RoundingMode.HALF_UP)
        .longValueExact()
}.getOrNull()

private fun ReportDateRange.monthKeys(existingMonthKeys: List<String>): List<String> {
    if (start.year == 1) return existingMonthKeys
    var month = YearMonth.from(start)
    val endMonth = YearMonth.from(end)
    return buildList {
        while (!month.isAfter(endMonth)) {
            add(month.toString())
            month = month.plusMonths(1)
        }
    }
}
