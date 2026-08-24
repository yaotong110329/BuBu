package com.kumo.bubu.feature.fuel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.core.ui.components.LocalDateTimePickerField
import com.kumo.bubu.domain.model.FuelProduct
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class FuelFormScreenTest {
    @get:Rule
    val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun advancedSettingsKeepSecondaryFuelInputsHiddenUntilExpanded() {
        val events = mutableListOf<FuelFormEvent>()
        composeRule.setContent {
            BuBuTheme {
                var expanded by remember { mutableStateOf(false) }
                AdvancedSettingsSection(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    state = FuelFormUiState(isLoading = false),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onAllNodesWithText("油品").assertCountEquals(0)
        composeRule.onAllNodesWithText("本次是否加滿").assertCountEquals(0)

        composeRule.onNode(hasClickAction() and hasText("進階設定")).performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNode(hasClickAction() and hasText("92 無鉛"))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("本次是否加滿").assertIsDisplayed()

        assertEquals(FuelFormEvent.FuelProductChanged(FuelProduct.GASOLINE_92), events.last())
    }

    @Test
    fun dateAndTimePickerFieldUsesAnAccessibleTapOverlay() {
        var clickCount = 0
        composeRule.setContent {
            BuBuTheme {
                LocalDateTimePickerField(
                    value = "2026-08-24",
                    labelRes = com.kumo.bubu.R.string.fuel_date,
                    enabled = true,
                    isError = false,
                    onClick = { clickCount++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("日期")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clickCount)
    }
}
