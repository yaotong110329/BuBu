package com.kumo.bubu.feature.reports

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.domain.model.ReportCostCategory
import com.kumo.bubu.domain.model.ReportFuelEconomyPoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsScreenTest {
    @get:Rule val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun vehicleSelectorSwitchesTheSingleVehicleReport() {
        var selectedVehicleId: Long? = null
        composeRule.setContent {
            BuBuTheme {
                ReportsScreen(
                    state = ReportsUiState(
                        vehicles = listOf(ReportVehicleOption(7, "測試汽車", null), ReportVehicleOption(8, "測試機車", null)),
                        selectedVehicleId = 7,
                        isLoading = false,
                        report = reportPresentation(),
                    ),
                    onEvent = { event -> if (event is ReportsEvent.SelectVehicle) selectedVehicleId = event.vehicleId },
                    onOpenSource = {},
                )
            }
        }

        composeRule.onNodeWithTag("reports-vehicle-7").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-vehicle-8").performClick()
        assertEquals(8L, selectedVehicleId)
    }

    @Test
    fun oneFuelEconomyPointShowsHelpfulStateInsteadOfSinglePointChart() {
        composeRule.setContent {
            BuBuTheme {
                ReportsScreen(
                    state = ReportsUiState(
                        vehicles = listOf(ReportVehicleOption(7, "測試汽車", null)),
                        selectedVehicleId = 7,
                        isLoading = false,
                        report = reportPresentation(fuelPoints = listOf(fuelPoint(44, 20_000, 13_200))),
                    ),
                    onEvent = {},
                    onOpenSource = {},
                )
            }
        }

        composeRule.onNodeWithText("目前油耗 13.2 km/L").assertIsDisplayed()
        composeRule.onNodeWithText("再新增 1 筆有效加油紀錄後顯示趨勢。").assertIsDisplayed()
        composeRule.onAllNodesWithTag("reports-fuel-economy-line-chart").assertCountEquals(0)
    }

    @Test
    fun periodSelectorUsesTheFourSupportedSharedRanges() {
        var selectedPeriod: ReportPeriod? = null
        composeRule.setContent {
            BuBuTheme {
                ReportsScreen(
                    state = ReportsUiState(
                        vehicles = listOf(ReportVehicleOption(7, "測試汽車", null)),
                        selectedVehicleId = 7,
                        isLoading = false,
                    ),
                    onEvent = { event -> if (event is ReportsEvent.SelectPeriod) selectedPeriod = event.period },
                    onOpenSource = {},
                )
            }
        }

        composeRule.onNodeWithText("近 6 個月").assertIsDisplayed()
        composeRule.onNodeWithText("1 年").assertIsDisplayed()
        composeRule.onNodeWithText("2 年").assertIsDisplayed()
        composeRule.onNodeWithText("全部").performClick()
        assertEquals(ReportPeriod.ALL, selectedPeriod)
    }

    @Test
    fun monthlyCostCanSwitchToFuelOrServiceSeries() {
        composeRule.setContent {
            BuBuTheme {
                ReportsScreen(
                    state = ReportsUiState(
                        vehicles = listOf(ReportVehicleOption(7, "測試汽車", null)),
                        selectedVehicleId = 7,
                        isLoading = false,
                        report = reportPresentation(monthlyTotals = listOf(ReportMonthUi("2026-08", 1_000, 600, 400))),
                    ),
                    onEvent = {},
                    onOpenSource = {},
                )
            }
        }

        composeRule.onNodeWithTag("reports-monthly-cost-chart").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-cost-series-fuel").performClick()
        composeRule.onNodeWithTag("reports-cost-series-service").performClick()
        composeRule.onNodeWithTag("reports-monthly-cost-chart").assertIsDisplayed()
    }

    private fun reportPresentation(
        fuelPoints: List<ReportFuelEconomyPoint> = emptyList(),
        monthlyTotals: List<ReportMonthUi> = emptyList(),
    ) = ReportPresentation(
        hasData = true,
        totalCostTwd = 1_000,
        monthlyAverageCostTwd = 167,
        categoryTotals = ReportCostCategory.entries.associateWith { 0L },
        monthlyTotals = monthlyTotals,
        serviceMonthlyTotals = emptyList(),
        fuelEconomy = ReportFuelEconomyUi(
            vehicleName = "測試汽車",
            averageMilliKmPerLiter = fuelPoints.lastOrNull()?.milliKmPerLiter,
            points = fuelPoints,
            trendStartEpochDay = fuelPoints.minOfOrNull(ReportFuelEconomyPoint::dateEpochDay) ?: 0L,
            trendEndEpochDay = fuelPoints.maxOfOrNull(ReportFuelEconomyPoint::dateEpochDay) ?: 0L,
        ),
        costPerKm = ReportCostPerKmUi("測試汽車", null, null),
        mileage = ReportMileageUi("測試汽車", emptyList()),
    )

    private fun fuelPoint(id: Long, day: Long, value: Long) = ReportFuelEconomyPoint(
        vehicleId = 7,
        dateEpochDay = day,
        fuelRecordId = id,
        milliKmPerLiter = value,
        distanceKm = 132,
        volumeMl = 10_000,
    )
}
