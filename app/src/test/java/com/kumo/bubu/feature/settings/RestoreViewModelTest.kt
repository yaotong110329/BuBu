package com.kumo.bubu.feature.settings

import com.kumo.bubu.domain.repository.RestorePreview
import com.kumo.bubu.domain.repository.RestoreRepository
import com.kumo.bubu.domain.repository.RestoreResult
import com.kumo.bubu.domain.repository.RecoveryBackup
import com.kumo.bubu.domain.repository.BackupTooLargeException
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.ViewModelStore
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

    @Test
    fun reportsOversizedRecoveryWithoutClaimingRestoreCompleted() = runTest {
        val repository = FakeRestoreRepository(IllegalStateException("Recovery creation failed", BackupTooLargeException()))
        val viewModel = RestoreViewModel(repository)
        viewModel.preview("content://backup")
        advanceUntilIdle()
        viewModel.restore()
        advanceUntilIdle()
        assertEquals(RestoreError.BACKUP_TOO_LARGE, viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.completed)
        assertEquals(false, viewModel.uiState.value.isRestoring)
    }

    @Test fun cloudPreviewCancelCleansOnceButLocalSourceIsNeverDiscarded() = runTest {
        val cloud = FakeCloudBackupRepository()
        val download = cloud.downloadBackup("token", "1")
        val viewModel = RestoreViewModel(FakeRestoreRepository(), cloud)
        viewModel.previewCloudDownload("content://owned-download", download)
        advanceUntilIdle()
        viewModel.clear()
        viewModel.clear()
        viewModel.preview("content://provider-original")
        advanceUntilIdle()
        viewModel.clear()
        assertEquals(listOf(download), cloud.discardedDownloads)
    }

    @Test fun clearingAnInFlightPreviewCannotReopenItsOldConfirmation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val cloud = FakeCloudBackupRepository()
        val download = cloud.downloadBackup("token", "1")
        val viewModel = RestoreViewModel(FakeRestoreRepository(previewGate = gate), cloud)
        viewModel.previewCloudDownload("content://owned-download", download)
        advanceUntilIdle()
        viewModel.clear()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.preview)
        assertEquals(false, viewModel.uiState.value.isLoadingPreview)
        assertEquals(listOf(download), cloud.discardedDownloads)
    }

    @Test fun successfulRestoreAndInvalidPreviewBothReleaseTheirDownload() = runTest {
        val cloud = FakeCloudBackupRepository()
        val download = cloud.downloadBackup("token", "1")
        val viewModel = RestoreViewModel(FakeRestoreRepository(), cloud)
        viewModel.previewCloudDownload("content://owned-download", download)
        advanceUntilIdle()
        viewModel.restore()
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.completed)
        assertEquals(listOf(download), cloud.discardedDownloads)
        val invalid = RestoreViewModel(FakeRestoreRepository(previewFailure = IllegalArgumentException()), cloud)
        invalid.previewCloudDownload("content://bad-download", download)
        advanceUntilIdle()
        assertEquals(RestoreError.INVALID_BACKUP, invalid.uiState.value.error)
        assertEquals(listOf(download, download), cloud.discardedDownloads)
    }

    @Test fun removingTheRestoreOwnerReleasesUnconfirmedDownload() = runTest {
        val cloud = FakeCloudBackupRepository()
        val download = cloud.downloadBackup("token", "1")
        val viewModel = RestoreViewModel(FakeRestoreRepository(), cloud)
        val store = ViewModelStore()
        store.put("restore", viewModel)
        viewModel.previewCloudDownload("content://owned-download", download)
        advanceUntilIdle()
        store.clear()
        assertEquals(listOf(download), cloud.discardedDownloads)
    }
}

private class FakeRestoreRepository(
    private val restoreFailure: Throwable? = null,
    private val previewFailure: Throwable? = null,
    private val previewGate: CompletableDeferred<Unit>? = null,
) : RestoreRepository {
    var restoredSource: String? = null

    override suspend fun preview(sourceUriString: String): RestorePreview {
        previewGate?.await()
        previewFailure?.let { throw it }
        return RestorePreview(
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
    }

    override suspend fun restore(sourceUriString: String): RestoreResult {
        restoreFailure?.let { throw it }
        restoredSource = sourceUriString
        return RestoreResult("recovery.bubu")
    }

    override suspend fun getLatestRecoveryBackup(): RecoveryBackup? = null

    override suspend fun exportLatestRecoveryBackup(destinationUriString: String): RecoveryBackup =
        RecoveryBackup("recovery.bubu", 1, 1)

    override suspend fun deleteLatestRecoveryBackup(): Boolean = true
}
