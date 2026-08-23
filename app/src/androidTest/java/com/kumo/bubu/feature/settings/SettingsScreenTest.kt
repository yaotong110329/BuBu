package com.kumo.bubu.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.kumo.bubu.core.ui.theme.BuBuTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun groupsSettingsAndKeepsTheTaxReminderSwitch() {
        var enabled = true
        composeRule.setContent {
            BuBuTheme {
                SettingsScreen(
                    onManageVehicles = {}, onManageServiceSettings = {},
                    statutoryReminderSettingsUiState = StatutoryReminderSettingsUiState(
                        taxAndFeeEnabled = true, verifiedDate = "2026-08-16", isLoading = false,
                    ),
                    onTaxAndFeeEnabledChange = { enabled = it },
                )
            }
        }

        composeRule.onNodeWithText("車輛").assertIsDisplayed()
        composeRule.onNodeWithText("提醒").assertIsDisplayed()
        composeRule.onNodeWithText("資料與備份").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-tax-fee-switch").performClick()
        assertFalse(enabled)
    }

    @Test
    fun exposesCsvExportAndNoLongerShowsLegacyFixtureImport() {
        var opened = false
        composeRule.setContent {
            BuBuTheme { SettingsScreen(onManageVehicles = {}, onManageServiceSettings = {}, onExportCsv = { opened = true }) }
        }

        composeRule.onNodeWithTag("settings-csv-export").performClick()
        composeRule.onAllNodesWithText("匯入舊測試資料").assertCountEquals(0)
        org.junit.Assert.assertTrue(opened)
    }

    @Test
    fun settingsScrollsToTheLastBackupAction() {
        composeRule.setContent {
            BuBuTheme { SettingsScreen(onManageVehicles = {}, onManageServiceSettings = {}) }
        }

        composeRule.onNodeWithTag("settings-scroll-list")
            .performScrollToNode(hasTestTag("settings-restore-backup"))
        composeRule.onNodeWithTag("settings-restore-backup").assertIsDisplayed()
    }
}
