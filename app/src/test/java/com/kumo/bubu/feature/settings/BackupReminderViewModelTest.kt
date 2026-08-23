package com.kumo.bubu.feature.settings

import com.kumo.bubu.core.notification.BackupReminderScheduler
import com.kumo.bubu.domain.repository.BackupReminderSettings
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupReminderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun enablingReminderPersistsPreferenceAndSchedulesCheck() = runTest {
        val settings = FakeBackupReminderSettings()
        val scheduler = FakeBackupReminderScheduler()
        val viewModel = BackupReminderViewModel(settings, scheduler)

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertTrue(settings.enabled.value)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun disablingReminderCancelsCheck() = runTest {
        val settings = FakeBackupReminderSettings(initialEnabled = true)
        val scheduler = FakeBackupReminderScheduler()
        val viewModel = BackupReminderViewModel(settings, scheduler)

        advanceUntilIdle()
        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertFalse(settings.enabled.value)
        assertTrue(scheduler.cancelled)
    }
}

private class FakeBackupReminderSettings(initialEnabled: Boolean = false) : BackupReminderSettings {
    val enabled = MutableStateFlow(initialEnabled)
    private val lastBackup = MutableStateFlow<Long?>(null)

    override fun observeEnabled(): Flow<Boolean> = enabled

    override fun observeLastSuccessfulBackupEpochDay(): Flow<Long?> = lastBackup

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override suspend fun recordSuccessfulBackup(epochDay: Long) {
        lastBackup.value = epochDay
    }
}

private class FakeBackupReminderScheduler : BackupReminderScheduler {
    var scheduled = false
    var cancelled = false

    override fun scheduleDailyCheck() {
        scheduled = true
    }

    override fun cancelDailyCheck() {
        cancelled = true
    }
}
