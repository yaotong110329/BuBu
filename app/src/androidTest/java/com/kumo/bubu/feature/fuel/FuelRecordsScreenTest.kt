package com.kumo.bubu.feature.fuel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.domain.model.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class FuelRecordsScreenTest {
    @get:Rule
    val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun recentFuelRecordExposesEditAndDeleteActions() {
        val events = mutableListOf<FuelRecordsEvent>()
        val editedIds = mutableListOf<Long>()
        composeRule.setContent {
            BuBuTheme {
                FuelRecordsScreen(
                    state = FuelRecordsUiState(records = listOf(row()), currentVehicleId = 3, isLoading = false),
                    onEvent = events::add,
                    onAddFuel = {},
                    onEditFuel = editedIds::add,
                )
            }
        }

        composeRule.onNodeWithText("編輯").performClick()
        composeRule.onNodeWithText("刪除").performClick()

        assertEquals(listOf(8L), editedIds)
        assertEquals(FuelRecordsEvent.RequestDelete(8), events.single())
    }

    @Test
    fun deleteConfirmationEmitsConfirmedDelete() {
        val events = mutableListOf<FuelRecordsEvent>()
        composeRule.setContent {
            BuBuTheme {
                FuelRecordsScreen(
                    state = FuelRecordsUiState(
                        records = listOf(row()),
                        currentVehicleId = 3,
                        deleteConfirmationRecordId = 8,
                        isLoading = false,
                    ),
                    onEvent = events::add,
                    onAddFuel = {},
                    onEditFuel = {},
                )
            }
        }

        composeRule.onNodeWithText("永久刪除加油紀錄？").assertIsDisplayed()
        composeRule.onAllNodesWithText("刪除").assertCountEquals(2)[1].performClick()
        assertEquals(FuelRecordsEvent.ConfirmDelete, events.single())
    }

    @Test
    fun deleteFailureShowsDismissibleFeedback() {
        val events = mutableListOf<FuelRecordsEvent>()
        composeRule.setContent {
            BuBuTheme {
                FuelRecordsScreen(
                    state = FuelRecordsUiState(deleteFailed = true, isLoading = false),
                    onEvent = events::add,
                    onAddFuel = {},
                    onEditFuel = {},
                )
            }
        }

        composeRule.onNodeWithText("無法刪除加油紀錄").assertIsDisplayed()
        composeRule.onNodeWithText("確認").performClick()
        assertEquals(FuelRecordsEvent.DismissError, events.single())
    }

    private fun row() = FuelRecordRow(
        record = FuelRecord(
            id = 8,
            publicId = "fuel-public-id",
            vehicleId = 3,
            dateEpochDay = 20_000,
            timeMinuteOfDay = null,
            sequenceInDay = 0,
            odometerKm = 1_234,
            fuelVolumeMl = 4_270,
            pricePerLiterMilli = 31_700,
            totalCostTwd = 135,
            isFullTank = true,
            fuelProduct = null,
            note = null,
            createdAt = 1,
            updatedAt = 1,
        ),
        vehicleName = "家用車",
    )
}
