package com.kumo.bubu.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun dashboardHeaderUsesBuBuOnly() {
        composeRule.setContent {
            BuBuTheme {
                DashboardScreen(
                    state = DashboardUiState(isLoading = false),
                    onAddVehicle = {},
                    onOpenVehicleHistory = {},
                    onAddFuel = {},
                    onAddService = {},
                )
            }
        }

        composeRule.onNodeWithText("BuBu").assertIsDisplayed()
    }

    @Test
    fun vehicleAddButtonOpensSheetAndStartsFuelForThatVehicle() {
        var selectedFuelVehicleId: Long? = null
        composeRule.setContent {
            BuBuTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        vehicles = listOf(
                            item(id = 3, name = "RAV4", plate = "ABC-1234"),
                            item(id = 4, name = "JET SL", plate = "XYZ-5678", type = VehicleType.MOTORCYCLE),
                        ),
                        isLoading = false,
                    ),
                    onAddVehicle = {},
                    onOpenVehicleHistory = {},
                    onAddFuel = { selectedFuelVehicleId = it },
                    onAddService = {},
                )
            }
        }

        composeRule.onNodeWithText("RAV4").assertIsDisplayed()
        composeRule.onNodeWithText("JET SL").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard-add-3").performClick()
        composeRule.onNodeWithText("新增「RAV4」紀錄").assertIsDisplayed()
        composeRule.onNodeWithText("加油").performClick()

        assertEquals(3L, selectedFuelVehicleId)
    }

    @Test
    fun emptyGarageOffersFirstVehicleAction() {
        var addRequested = false
        composeRule.setContent {
            BuBuTheme {
                DashboardScreen(
                    state = DashboardUiState(isLoading = false),
                    onAddVehicle = { addRequested = true },
                    onOpenVehicleHistory = {},
                    onAddFuel = {},
                    onAddService = {},
                )
            }
        }

        composeRule.onNodeWithText("還沒有車輛").assertIsDisplayed()
        composeRule.onNodeWithText("新增車輛").performClick()

        assertEquals(true, addRequested)
    }

    @Test
    fun vehicleCardOpensThatVehiclesHistory() {
        var openedVehicleId: Long? = null
        composeRule.setContent {
            BuBuTheme {
                DashboardScreen(
                    state = DashboardUiState(vehicles = listOf(item(3, "RAV4", "ABC-1234")), isLoading = false),
                    onAddVehicle = {},
                    onOpenVehicleHistory = { openedVehicleId = it },
                    onAddFuel = {},
                    onAddService = {},
                )
            }
        }

        composeRule.onNodeWithText("RAV4").performClick()

        assertEquals(3L, openedVehicleId)
    }

    private fun item(
        id: Long,
        name: String,
        plate: String,
        type: VehicleType = VehicleType.CAR,
    ) = VehicleDashboardItem(
        vehicleId = id,
        name = name,
        vehicleType = type,
        licensePlate = plate,
        latestOdometerKm = 12_345,
        averageFuelEconomyMilliKmPerLiter = 13_800,
    )
}
