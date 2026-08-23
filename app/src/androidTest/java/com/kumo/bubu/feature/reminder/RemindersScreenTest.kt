package com.kumo.bubu.feature.reminder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.VehicleReminder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersScreenTest {
    @get:Rule
    val composeRule = createComposeRule(UnconfinedTestDispatcher())

    @Test
    fun showsOverdueManualReminderAndSendsCompleteAction() {
        var received: ReminderEvent? = null
        composeRule.setContent {
            BuBuTheme {
                RemindersScreen(
                    state = RemindersUiState(
                        vehicles = listOf(ReminderVehicleOption(1, "RAV4")),
                        rows = listOf(
                            ReminderRow(
                                reminder = reminder(),
                                vehicleName = "RAV4",
                                currentOdometerKm = 10_100,
                                status = ReminderStatus.OVERDUE,
                            ),
                        ),
                        isLoading = false,
                    ),
                    onEvent = { received = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("已逾期").assertIsDisplayed()
        composeRule.onNodeWithText("手動提醒").assertIsDisplayed()
        composeRule.onNodeWithTag("reminder-7").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("提醒操作").performClick()
        composeRule.onNodeWithText("標示完成").performClick()

        assertEquals(ReminderEvent.Complete(7), received)
    }

    @Test
    fun showsInspectionWindowAndOfficialDeadlineDisclaimer() {
        composeRule.setContent {
            BuBuTheme {
                RemindersScreen(
                    state = RemindersUiState(
                        vehicles = listOf(ReminderVehicleOption(1, "RAV4")),
                        rows = listOf(
                            ReminderRow(
                                reminder = reminder().copy(
                                    source = ReminderSource.PERIODIC_INSPECTION,
                                    title = "定期驗車",
                                    dueOdometerKm = null,
                                    referenceDateEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(),
                                    dueDateEpochDay = LocalDate.of(2026, 11, 20).toEpochDay(),
                                ),
                                vehicleName = "RAV4",
                                currentOdometerKm = 10_100,
                                status = ReminderStatus.NORMAL,
                            ),
                        ),
                        isLoading = false,
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("定期驗車提醒").assertIsDisplayed()
        composeRule.onNodeWithText("行照指定日：2026-10-20", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("可辦理期限：2026-11-20", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("實際期限以當年度主管機關公告、繳款書或行車執照為準。").assertIsDisplayed()
    }

    private fun reminder() = VehicleReminder(
        id = 7,
        publicId = "manual-7",
        vehicleId = 1,
        source = ReminderSource.MANUAL,
        sourceServiceItemId = null,
        title = "驗車",
        dueOdometerKm = 10_000,
        dueDateEpochDay = null,
        completedByServiceRecordId = null,
        completedAt = null,
        snoozedUntilEpochDay = null,
        isEnabled = true,
        createdAt = 1,
        updatedAt = 1,
    )
}
