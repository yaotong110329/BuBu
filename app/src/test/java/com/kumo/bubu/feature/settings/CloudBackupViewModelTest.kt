package com.kumo.bubu.feature.settings

import com.kumo.bubu.data.repository.CloudBackupException
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.model.CloudBackupError
import com.kumo.bubu.domain.repository.CloudBackupDownload
import com.kumo.bubu.domain.repository.CloudBackupRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun authorizationConnectsTheSelectedAccountAndLoadsBackups() = runTest {
        val repository = FakeCloudBackupRepository()
        val viewModel = CloudBackupViewModel(repository)

        viewModel.connect()
        assertEquals(CloudBackupAuthorizationAction.Connect, viewModel.authorizationRequests.first())
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals("driver@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
    }

    @Test
    fun sortsCloudBackupsBeforeDisplayingThem() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2), backup(8), backup(4)))
        val viewModel = CloudBackupViewModel(repository)

        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.LoadBackups, "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals(listOf("8", "4", "2"), viewModel.uiState.value.backups.map(CloudBackup::id))
    }

    @Test
    fun blocksUnsupportedDownloadedFormatsBeforeRestore() = runTest {
        val repository = FakeCloudBackupRepository(downloadFailure = CloudBackupException(
            "download failed",
            IllegalArgumentException("Backup format is unsupported."),
        ))
        val viewModel = CloudBackupViewModel(repository)

        viewModel.download("1")
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Download("1"), "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals(CloudBackupError.UnsupportedFormat, viewModel.uiState.value.error)
    }

    @Test
    fun deletesTheSelectedCloudBackupThenRefreshesTheList() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
        val viewModel = CloudBackupViewModel(repository)

        viewModel.delete("2")
        assertEquals(CloudBackupAuthorizationAction.Delete("2"), viewModel.authorizationRequests.first())
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Delete("2"), "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals(listOf("2"), repository.deletedBackupIds)
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
    }

    private fun backup(createdAt: Long) = CloudBackup(
        id = createdAt.toString(),
        fileName = "bubu-backup-$createdAt.bubu",
        createdAtEpochMillis = createdAt,
        modifiedAtEpochMillis = createdAt,
        sizeBytes = 1,
        appVersion = "1.0.0",
        formatVersion = 1,
        vehicleCount = 0,
        fuelRecordCount = 0,
        maintenanceRecordCount = 0,
    )
}

private class FakeCloudBackupRepository(
    private val backups: List<CloudBackup> = emptyList(),
    private val downloadFailure: Throwable? = null,
) : CloudBackupRepository {
    val deletedBackupIds = mutableListOf<String>()
    private val connection = MutableStateFlow<CloudBackupConnection>(CloudBackupConnection.NotConnected)

    override fun observeConnection(): Flow<CloudBackupConnection> = connection

    override suspend fun rememberConnection(account: CloudBackupAccount) {
        connection.value = CloudBackupConnection.Connected(account)
    }

    override suspend fun clearConnection() {
        connection.value = CloudBackupConnection.NotConnected
    }

    override suspend fun markNeedsAuthorization() {
        connection.value = CloudBackupConnection.NeedsAuthorization
    }

    override suspend fun uploadBackup(accessToken: String): CloudBackup = backup(1)

    override suspend fun listBackups(accessToken: String): List<CloudBackup> = backups

    override suspend fun downloadBackup(accessToken: String, backupId: String): CloudBackupDownload {
        downloadFailure?.let { throw it }
        return CloudBackupDownload(backup(1), "cache.bubu")
    }

    override suspend fun deleteBackup(accessToken: String, backupId: String) {
        deletedBackupIds += backupId
    }

    private fun backup(createdAt: Long) = CloudBackup(
        id = createdAt.toString(),
        fileName = "bubu-backup-$createdAt.bubu",
        createdAtEpochMillis = createdAt,
        modifiedAtEpochMillis = createdAt,
        sizeBytes = 1,
        appVersion = "1.0.0",
        formatVersion = 1,
        vehicleCount = 0,
        fuelRecordCount = 0,
        maintenanceRecordCount = 0,
    )
}
