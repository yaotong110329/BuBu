package com.kumo.bubu.feature.settings

import com.kumo.bubu.data.repository.CloudBackupException
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.model.CloudBackupError
import com.kumo.bubu.domain.repository.CloudBackupDownload
import com.kumo.bubu.domain.repository.CloudBackupRetentionException
import com.kumo.bubu.domain.repository.CloudBackupRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
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
        assertEquals(CloudBackupAuthorizationAction.Connect, viewModel.authorizationRequests.first().action)
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals("driver@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
    }

    @Test
    fun rejectsUploadAuthorizationForAnotherAccountWithoutChangingTheConnection() = runTest {
        val viewModel = CloudBackupViewModel(FakeCloudBackupRepository())
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()

        viewModel.upload()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Upload, "b@example.com", "b-token")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.backupCompleted)
        assertEquals("a@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
    }

    @Test
    fun rejectsUploadForAnotherAccountAfterAuthorizationExpires() = runTest {
        val viewModel = CloudBackupViewModel(FakeCloudBackupRepository())
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()
        viewModel.onAuthorizationFailed(CloudBackupError.AuthorizationExpired)
        advanceUntilIdle()

        viewModel.upload()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Upload, "b@example.com", "b-token")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.backupCompleted)
        assertEquals(CloudBackupConnection.NeedsAuthorization, viewModel.uiState.value.connection)
        assertEquals(CloudBackupError.AuthorizationExpired, viewModel.uiState.value.error)
    }

    @Test
    fun rejectsListDownloadAndDeleteAuthorizedForAnotherAccount() = runTest {
        listOf(
            CloudBackupAuthorizationAction.LoadBackups,
            CloudBackupAuthorizationAction.Download("2"),
            CloudBackupAuthorizationAction.Delete("2"),
        ).forEach { action ->
            val viewModel = CloudBackupViewModel(FakeCloudBackupRepository(backups = listOf(backup(2))))
            val restores = mutableListOf<CloudRestoreReady>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.restoreRequests.collect { restores += it }
            }
            viewModel.connect()
            viewModel.authorizationRequests.first()
            viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
            advanceUntilIdle()

            when (action) {
                CloudBackupAuthorizationAction.LoadBackups -> viewModel.loadBackups()
                is CloudBackupAuthorizationAction.Download -> viewModel.download(action.backupId)
                is CloudBackupAuthorizationAction.Delete -> viewModel.delete(action.backupId)
                else -> error("Unexpected test operation.")
            }
            viewModel.authorizationRequests.first()
            viewModel.onAuthorized(action, "b@example.com", "b-token")
            advanceUntilIdle()

            assertEquals(CloudBackupError.AccountMismatch, viewModel.uiState.value.error)
            assertEquals("a@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
            assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
            assertEquals(emptyList<CloudRestoreReady>(), restores)
        }
    }

    @Test
    fun switchingAccountsClearsTheOldListEvenWhenTheNewListFailsToLoad() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
        val viewModel = CloudBackupViewModel(repository)
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
        repository.listFailure = IOException("Test network unavailable.")

        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "b@example.com", "b-token")
        advanceUntilIdle()

        assertEquals(emptyList<CloudBackup>(), viewModel.uiState.value.backups)
        assertEquals("b@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
        assertEquals(CloudBackupError.NetworkUnavailable, viewModel.uiState.value.error)
    }

    @Test
    fun doesNotAuthorizeBackupSelectionsLeftOverFromThePreviousAccount() = runTest {
        listOf(
            CloudBackupAuthorizationAction.Download("2"),
            CloudBackupAuthorizationAction.Delete("2"),
        ).forEach { action ->
            val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
            val viewModel = CloudBackupViewModel(repository)
            viewModel.connect()
            viewModel.authorizationRequests.first()
            viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
            advanceUntilIdle()
            repository.listFailure = IOException("Test network unavailable.")
            viewModel.connect()
            viewModel.authorizationRequests.first()
            viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "b@example.com", "b-token")
            advanceUntilIdle()
            val requests = mutableListOf<CloudAuthorizationRequest>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.authorizationRequests.collect { requests += it }
            }

            when (action) {
                is CloudBackupAuthorizationAction.Download -> viewModel.download(action.backupId)
                is CloudBackupAuthorizationAction.Delete -> viewModel.delete(action.backupId)
                else -> error("Unexpected test operation.")
            }
            advanceUntilIdle()

            assertEquals(emptyList<CloudAuthorizationRequest>(), requests)
            assertEquals(false, viewModel.uiState.value.isAuthorizing)
        }
    }

    @Test
    fun rejectsAuthorizedBackupSelectionsFromThePreviousAccount() = runTest {
        listOf(
            CloudBackupAuthorizationAction.Download("2") to CloudBackupError.DownloadFailed,
            CloudBackupAuthorizationAction.Delete("2") to CloudBackupError.DeleteFailed,
        ).forEach { (action, expectedError) ->
            val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
            val viewModel = CloudBackupViewModel(repository)
            viewModel.connect()
            viewModel.authorizationRequests.first()
            viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
            advanceUntilIdle()
            when (action) {
                is CloudBackupAuthorizationAction.Download -> viewModel.download(action.backupId)
                is CloudBackupAuthorizationAction.Delete -> viewModel.delete(action.backupId)
                else -> error("Unexpected test operation.")
            }
            val oldRequest = viewModel.authorizationRequests.first()
            viewModel.onAuthorizationCancelled()
            repository.listFailure = IOException("Test network unavailable.")
            viewModel.connect()
            viewModel.authorizationRequests.first()
            viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "b@example.com", "b-token")
            advanceUntilIdle()
            val restores = mutableListOf<CloudRestoreReady>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.restoreRequests.collect { restores += it }
            }

            viewModel.onAuthorized(oldRequest.action, "b@example.com", "b-token")
            advanceUntilIdle()

            assertEquals(expectedError, viewModel.uiState.value.error)
            assertEquals(emptyList<CloudRestoreReady>(), restores)
            assertEquals("b@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
        }
    }

    @Test
    fun cancellationAndAuthorizationFailurePreserveTheExistingAccountAndList() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
        val original = CloudBackupAccount("a@example.com", 42)
        repository.rememberConnection(original)
        val viewModel = CloudBackupViewModel(repository)
        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.LoadBackups, "a@example.com", "a-token")
        advanceUntilIdle()

        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorizationCancelled()
        advanceUntilIdle()
        assertEquals(CloudBackupConnection.Connected(original), viewModel.uiState.value.connection)
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
        assertEquals(false, viewModel.uiState.value.isAuthorizing)

        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorizationFailed(CloudBackupError.NetworkUnavailable)
        advanceUntilIdle()
        assertEquals(CloudBackupConnection.Connected(original), viewModel.uiState.value.connection)
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
        assertEquals(CloudBackupError.NetworkUnavailable, viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isAuthorizing)

        viewModel.disconnected()
        advanceUntilIdle()
        assertEquals(CloudBackupConnection.NotConnected, viewModel.uiState.value.connection)
        assertEquals(emptyList<CloudBackup>(), viewModel.uiState.value.backups)
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()
        assertEquals("a@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
    }

    @Test
    fun anObservedAccountChangeInvalidatesThePreviousBackupList() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
        val viewModel = CloudBackupViewModel(repository)
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))

        repository.rememberConnection(CloudBackupAccount("b@example.com"))
        advanceUntilIdle()

        assertEquals("b@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
        assertEquals(emptyList<CloudBackup>(), viewModel.uiState.value.backups)
    }

    @Test
    fun aSuccessfulAccountSwitchKeepsTheNewAccountsLoadedBackups() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
        val viewModel = CloudBackupViewModel(repository)
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()
        repository.listResult = listOf(backup(8))

        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "b@example.com", "b-token")
        advanceUntilIdle()

        assertEquals("b@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
        assertEquals(listOf("8"), viewModel.uiState.value.backups.map(CloudBackup::id))
    }

    @Test
    fun anAccountChangeDoesNotShowThePreviousAccountsUploadAsCompleted() = runTest {
        val repository = FakeCloudBackupRepository()
        val viewModel = CloudBackupViewModel(repository)
        viewModel.connect()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Connect, "a@example.com", "a-token")
        advanceUntilIdle()
        viewModel.upload()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Upload, "a@example.com", "a-token")
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.backupCompleted)
        repository.rememberConnection(CloudBackupAccount("A@example.com", 42))
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.backupCompleted)

        repository.rememberConnection(CloudBackupAccount("b@example.com"))
        advanceUntilIdle()

        assertEquals("b@example.com", (viewModel.uiState.value.connection as CloudBackupConnection.Connected).account.email)
        assertEquals(false, viewModel.uiState.value.backupCompleted)
    }

    @Test
    fun sortsCloudBackupsBeforeDisplayingThem() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2), backup(8), backup(4)))
        repository.rememberConnection(CloudBackupAccount("driver@example.com"))
        val viewModel = CloudBackupViewModel(repository)

        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.LoadBackups, "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals(listOf("8", "4", "2"), viewModel.uiState.value.backups.map(CloudBackup::id))
    }

    @Test
    fun blocksUnsupportedDownloadedFormatsBeforeRestore() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(1)), downloadFailure = CloudBackupException(
            "download failed",
            IllegalArgumentException("Backup format is unsupported."),
        ))
        repository.rememberConnection(CloudBackupAccount("driver@example.com"))
        val viewModel = CloudBackupViewModel(repository)
        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.LoadBackups, "driver@example.com", "token")
        advanceUntilIdle()

        viewModel.download("1")
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Download("1"), "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals(CloudBackupError.UnsupportedFormat, viewModel.uiState.value.error)
    }

    @Test
    fun deletesTheSelectedCloudBackupThenRefreshesTheList() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(2)))
        repository.rememberConnection(CloudBackupAccount("driver@example.com"))
        val viewModel = CloudBackupViewModel(repository)
        viewModel.loadBackups()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.LoadBackups, "driver@example.com", "token")
        advanceUntilIdle()

        viewModel.delete("2")
        assertEquals(CloudBackupAuthorizationAction.Delete("2"), viewModel.authorizationRequests.first().action)
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Delete("2"), "driver@example.com", "token")
        advanceUntilIdle()

        assertEquals(listOf("2"), repository.deletedBackupIds)
        assertEquals(listOf("2"), viewModel.uiState.value.backups.map(CloudBackup::id))
    }

    @Test
    fun cancellationAllowsRetryAndStaleResultCannotConsumeTheNewRequest() = runTest {
        val viewModel = CloudBackupViewModel(FakeCloudBackupRepository())
        viewModel.connect()
        val oldRequest = viewModel.authorizationRequests.first()
        viewModel.onAccountSelected(oldRequest.id, GoogleDriveAccount("driver@example.com"))
        viewModel.consumeAuthorizationResult(oldRequest.id)
        viewModel.onAuthorizationCancelled()
        viewModel.connect()
        val retry = viewModel.authorizationRequests.first()
        assertEquals(null, viewModel.consumeAuthorization(oldRequest.id))
        viewModel.onAccountSelected(retry.id, GoogleDriveAccount("retry@example.com"))
        assertEquals(null, viewModel.consumeAuthorizationResult(oldRequest.id))
        val result = viewModel.consumeAuthorizationResult(retry.id)
        assertEquals("retry@example.com", result?.account?.email)
        assertEquals(null, viewModel.consumeAuthorizationResult(retry.id))
        assertEquals(false, viewModel.uiState.value.isAuthorizing)
    }

    @Test
    fun retentionFailureStillShowsThatTheBackupWasUploaded() = runTest {
        val repository = FakeCloudBackupRepository(backups = listOf(backup(6)))
        repository.rememberConnection(CloudBackupAccount("a@example.com", 42))
        repository.uploadFailure = CloudBackupRetentionException(backup(6), IOException("Fixture cleanup unavailable"))
        val viewModel = CloudBackupViewModel(repository)
        advanceUntilIdle()
        viewModel.upload()
        viewModel.authorizationRequests.first()
        viewModel.onAuthorized(CloudBackupAuthorizationAction.Upload, "a@example.com", "fixture-token")
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.backupCompleted)
        assertEquals(CloudBackupError.RetentionIncomplete, viewModel.uiState.value.error)
        assertEquals(listOf(backup(6)), viewModel.uiState.value.backups)
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

internal class FakeCloudBackupRepository(
    private val backups: List<CloudBackup> = emptyList(),
    private val downloadFailure: Throwable? = null,
) : CloudBackupRepository {
    var uploadFailure: Throwable? = null
    var listFailure: Throwable? = null
    var listResult: List<CloudBackup> = backups
    val deletedBackupIds = mutableListOf<String>()
    val discardedDownloads = mutableListOf<CloudBackupDownload>()
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

    override suspend fun uploadBackup(accessToken: String): CloudBackup {
        uploadFailure?.let { throw it }
        return backup(1)
    }

    override suspend fun listBackups(accessToken: String): List<CloudBackup> {
        listFailure?.let { throw it }
        return listResult
    }

    override suspend fun downloadBackup(accessToken: String, backupId: String): CloudBackupDownload {
        downloadFailure?.let { throw it }
        return CloudBackupDownload(backup(1), "cache.bubu")
    }

    override fun discardDownload(download: CloudBackupDownload) { discardedDownloads += download }

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
