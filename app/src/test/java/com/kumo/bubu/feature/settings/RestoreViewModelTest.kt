package com.kumo.bubu.feature.settings

import com.kumo.bubu.domain.repository.RestorePreview
import com.kumo.bubu.domain.repository.RestoreRepository
import com.kumo.bubu.domain.repository.RestoreResult
import com.kumo.bubu.domain.repository.RecoveryBackup
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun previewsThenRestoresTheSameSelectedBackup() = runTest {
        val repository = FakeRestoreRepository()
        val viewModel = RestoreViewModel(repository)

        viewModel.preview("content://backup")
        advanceUntilIdle()
        viewModel.restore()
        advanceUntilIdle()

        assertEquals("content://backup", repository.restoredSource)
        assertEquals(true, viewModel.uiState.value.completed)
    }
}

private class FakeRestoreRepository : RestoreRepository {
    var restoredSource: String? = null

    override suspend fun preview(sourceUriString: String) = RestorePreview(
        createdAtEpochMillis = 1,
        appVersion = "1.0.0",
        vehicleCount = 1,
        fuelRecordCount = 2,
        serviceRecordCount = 3,
        serviceItemCount = 3,
        expenseRecordCount = 0,
        reminderCount = 0,
        attachmentCount = 0,
        totalByteCount = 1,
    )

    override suspend fun restore(sourceUriString: String): RestoreResult {
        restoredSource = sourceUriString
        return RestoreResult("recovery.bubu")
    }

    override suspend fun getLatestRecoveryBackup(): RecoveryBackup? = null

    override suspend fun exportLatestRecoveryBackup(destinationUriString: String): RecoveryBackup =
        RecoveryBackup("recovery.bubu", 1, 1)

    override suspend fun deleteLatestRecoveryBackup(): Boolean = true
}
