package com.kumo.bubu.feature.settings

import com.kumo.bubu.domain.repository.BackupRepository
import com.kumo.bubu.domain.repository.BackupResult
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reportsCreatedBackupAfterRepositorySucceeds() = runTest {
        val viewModel = BackupViewModel(
            backupRepository = object : BackupRepository {
                override suspend fun createBackup(destinationUriString: String) =
                    BackupResult("bubu-backup.bubu", 42)
            },
        )

        viewModel.createBackup("content://destination")
        advanceUntilIdle()

        assertEquals("bubu-backup.bubu", viewModel.uiState.value.createdFileName)
    }
}
