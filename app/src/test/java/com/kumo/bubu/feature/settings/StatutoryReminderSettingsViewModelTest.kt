package com.kumo.bubu.feature.settings

import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.VehicleReminder
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatutoryReminderSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun globalTaxAndFeeSwitchUpdatesTheDurableSettingAndAutomaticReminders() = runTest {
        val settings = FakeStatutorySettings()
        val reminders = FakeStatutorySettingsReminderRepository(settings)
        val viewModel = StatutoryReminderSettingsViewModel(settings, reminders)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.taxAndFeeEnabled)

        viewModel.setTaxAndFeeEnabled(false)
        advanceUntilIdle()

        assertFalse(settings.enabled.value)
        assertFalse(viewModel.uiState.value.taxAndFeeEnabled)
        assertFalse(viewModel.uiState.value.hasError)
    }
}

private class FakeStatutorySettings : StatutoryReminderSettings {
    val enabled = MutableStateFlow(true)

    override fun observeTaxAndFeeEnabled(): Flow<Boolean> = enabled

    override suspend fun setTaxAndFeeEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}

private class FakeStatutorySettingsReminderRepository(
    private val settings: StatutoryReminderSettings,
) : ReminderRepository {
    override fun observeReminders(): Flow<List<VehicleReminder>> = MutableStateFlow(emptyList())

    override suspend fun createManualReminder(input: ManualReminderInput): Long = 1

    override suspend fun completeReminder(id: Long) = Unit

    override suspend fun snoozeReminder(id: Long, untilEpochDay: Long) = Unit

    override suspend fun setReminderEnabled(id: Long, enabled: Boolean) = Unit

    override suspend fun setTaxAndFeeRemindersEnabled(enabled: Boolean) {
        settings.setTaxAndFeeEnabled(enabled)
    }

    override suspend fun ensureStatutoryReminder(
        vehicleId: Long,
        cycleYear: Int,
        source: com.kumo.bubu.domain.model.ReminderSource,
    ) = Unit

    override suspend fun refreshAutomaticReminders(today: LocalDate) = Unit
}
