package com.kumo.bubu.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsOnDashboard() {
        val destination = TopLevelDestination.DASHBOARD
        val dashboard = text(destination.labelRes)

        composeRule.onNodeWithTag(destination.route).assertIsSelected()
        composeRule.onAllNodesWithText(dashboard).assertCountEquals(1)
    }

    @Test
    fun userCanSwitchBetweenAllTopLevelDestinations() {
        val destinations = listOf(
            TopLevelDestination.REPORTS,
            TopLevelDestination.SETTINGS,
            TopLevelDestination.DASHBOARD,
        )

        destinations.forEach { destination ->
            val title = text(destination.labelRes)
            composeRule.onNodeWithTag(destination.route).performClick()
            composeRule.onNodeWithTag(destination.route).assertIsSelected()
            composeRule.onAllNodesWithText(title).assertCountEquals(
                if (destination == TopLevelDestination.DASHBOARD) 1 else 2,
            )
        }
    }

    @Test
    fun backReturnsToDashboard() {
        val dashboard = TopLevelDestination.DASHBOARD

        composeRule.onNodeWithTag(TopLevelDestination.REPORTS.route).performClick()
        composeRule.onNodeWithTag(TopLevelDestination.REPORTS.route).assertIsSelected()
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(dashboard.route).assertIsSelected()
        composeRule.onAllNodesWithText(text(dashboard.labelRes)).assertCountEquals(1)
    }

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)
}
