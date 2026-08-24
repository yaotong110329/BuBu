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
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
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

    @Test
    fun showsConnectGoogleDriveForAnUnlinkedInstallation() {
        var connectRequested = false
        composeRule.setContent {
            BuBuTheme {
                SettingsScreen(
                    onManageVehicles = {},
                    onManageServiceSettings = {},
                    onConnectGoogleDrive = { connectRequested = true },
                )
            }
        }

        composeRule.onNodeWithTag("settings-scroll-list")
            .performScrollToNode(hasTestTag("settings-google-drive-connect"))
        composeRule.onNodeWithTag("settings-google-drive-connect").performClick()

        org.junit.Assert.assertTrue(connectRequested)
    }

    @Test
    fun showsConnectedGoogleDriveBackupActions() {
        composeRule.setContent {
            BuBuTheme {
                SettingsScreen(
                    onManageVehicles = {},
                    onManageServiceSettings = {},
                    cloudBackupUiState = CloudBackupUiState(
                        connection = CloudBackupConnection.Connected(
                            CloudBackupAccount("driver@example.com", lastCloudBackupAtEpochMillis = 1),
                        ),
                        isUploading = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("settings-scroll-list")
            .performScrollToNode(hasTestTag("settings-google-drive-backup"))
        composeRule.onNodeWithText("driver@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("正在備份到 Google Drive…").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-google-drive-backup").assertIsDisplayed()
    }

    @Test
    fun displaysCloudBackupListBeforeDownload() {
        composeRule.setContent {
            BuBuTheme {
                CloudBackupListDialog(
                    state = CloudBackupUiState(backups = listOf(cloudBackup())),
                    onDismiss = {},
                    onRefresh = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("2 台車 · 168 筆加油 · 41 筆保養", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("下載並預覽").assertIsDisplayed()
    }

    private fun cloudBackup() = CloudBackup(
        id = "backup",
        fileName = "bubu-backup-2026-08-24-163000.bubu",
        createdAtEpochMillis = 1,
        modifiedAtEpochMillis = 1,
        sizeBytes = 1024,
        appVersion = "1.1.0",
        formatVersion = 1,
        vehicleCount = 2,
        fuelRecordCount = 168,
        maintenanceRecordCount = 41,
    )
}
