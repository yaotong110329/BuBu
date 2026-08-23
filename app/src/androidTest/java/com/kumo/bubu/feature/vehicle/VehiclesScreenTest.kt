package com.kumo.bubu.feature.vehicle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclesScreenTest {
    @get:Rule
    val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun listShowsCurrentAndArchivedVehicleStates() {
        val vehicles = listOf(vehicle(1, "家用車", "current"), vehicle(2, "舊車", "old", true))

        composeRule.setContent {
            BuBuTheme {
                VehiclesScreen(
                    uiState = VehiclesUiState(
                        vehicles = vehicles,
                        currentVehiclePublicId = "current",
                        isLoading = false,
                    ),
                    onEvent = {},
                    onAddVehicle = {},
                    onEditVehicle = {},
                )
            }
        }

        composeRule.onNodeWithText("目前車輛").assertIsDisplayed()
        composeRule.onNodeWithText("已封存").assertIsDisplayed()
    }

    @Test
    fun deleteActionRequestsConfirmation() {
        val events = mutableListOf<VehiclesEvent>()
        val target = vehicle(7, "待刪除車輛", "delete")
        composeRule.setContent {
            BuBuTheme {
                VehiclesScreen(
                    uiState = VehiclesUiState(vehicles = listOf(target), isLoading = false),
                    onEvent = events::add,
                    onAddVehicle = {},
                    onEditVehicle = {},
                )
            }
        }

        composeRule.onNodeWithText("刪除").performClick()

        assertEquals(VehiclesEvent.RequestDelete(7), events.single())
    }

    @Test
    fun listExposesSelectEditArchiveAndUnarchiveActions() {
        val events = mutableListOf<VehiclesEvent>()
        val editedIds = mutableListOf<Long>()
        val vehicles = listOf(vehicle(1, "使用中", "active"), vehicle(2, "封存車", "old", true))
        composeRule.setContent {
            BuBuTheme {
                VehiclesScreen(
                    uiState = VehiclesUiState(vehicles = vehicles, isLoading = false),
                    onEvent = events::add,
                    onAddVehicle = {},
                    onEditVehicle = editedIds::add,
                )
            }
        }

        composeRule.onNodeWithText("設為目前車輛").performClick()
        composeRule.onAllNodesWithText("編輯")[0].performClick()
        composeRule.onNodeWithText("封存").performClick()
        composeRule.onNodeWithText("解除封存").performClick()

        assertEquals(VehiclesEvent.SelectCurrent("active", 1), events[0])
        assertEquals(VehiclesEvent.Archive(1), events[1])
        assertEquals(VehiclesEvent.Unarchive(2), events[2])
        assertEquals(listOf(1L), editedIds)
    }

    @Test
    fun confirmationDialogEmitsConfirmedDelete() {
        val events = mutableListOf<VehiclesEvent>()
        val target = vehicle(9, "確認刪除", "confirm")
        composeRule.setContent {
            BuBuTheme {
                VehiclesScreen(
                    uiState = VehiclesUiState(
                        vehicles = listOf(target),
                        deleteConfirmationVehicleId = 9,
                        isLoading = false,
                    ),
                    onEvent = events::add,
                    onAddVehicle = {},
                    onEditVehicle = {},
                )
            }
        }

        composeRule.onNodeWithText("永久刪除車輛？").assertIsDisplayed()
        composeRule.onAllNodesWithText("刪除").assertCountEquals(2)[1].performClick()
        assertEquals(VehiclesEvent.ConfirmDelete, events.single())
    }

    private fun vehicle(id: Long, name: String, publicId: String, archived: Boolean = false) = Vehicle(
        id = id,
        publicId = publicId,
        name = name,
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 0,
        currentOdometerKm = 0,
        note = null,
        isArchived = archived,
        createdAt = 1,
        updatedAt = 1,
    )
}
