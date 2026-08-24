package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.data.repository.CloudBackupException
import com.kumo.bubu.data.cloud.drive.GoogleDriveHttpException
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.model.CloudBackupError
import com.kumo.bubu.domain.model.CloudBackupOrdering
import com.kumo.bubu.domain.repository.CloudBackupDownload
import com.kumo.bubu.domain.repository.CloudBackupRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class CloudBackupUiState(
    val connection: CloudBackupConnection = CloudBackupConnection.NotConnected,
    val backups: List<CloudBackup> = emptyList(),
    val isLoadingBackups: Boolean = false,
    val isUploading: Boolean = false,
    val isDownloading: Boolean = false,
    val isDeleting: Boolean = false,
    val error: CloudBackupError? = null,
    val backupCompleted: Boolean = false,
)

sealed interface CloudBackupAuthorizationAction {
    data object Connect : CloudBackupAuthorizationAction
    data object Upload : CloudBackupAuthorizationAction
    data object LoadBackups : CloudBackupAuthorizationAction
    data class Download(val backupId: String) : CloudBackupAuthorizationAction
    data class Delete(val backupId: String) : CloudBackupAuthorizationAction
}

data class CloudRestoreReady(val download: CloudBackupDownload)

class CloudBackupViewModel(
    private val repository: CloudBackupRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CloudBackupUiState())
    val uiState = mutableUiState.asStateFlow()

    private val authorizationChannel = Channel<CloudBackupAuthorizationAction>(Channel.BUFFERED)
    val authorizationRequests = authorizationChannel.receiveAsFlow()

    private val restoreChannel = Channel<CloudRestoreReady>(Channel.BUFFERED)
    val restoreRequests = restoreChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeConnection().collect { connection ->
                mutableUiState.value = mutableUiState.value.copy(connection = connection)
            }
        }
    }

    fun connect() = requestAuthorization(CloudBackupAuthorizationAction.Connect)

    fun upload() = requestAuthorization(CloudBackupAuthorizationAction.Upload)

    fun loadBackups() = requestAuthorization(CloudBackupAuthorizationAction.LoadBackups)

    fun download(backupId: String) = requestAuthorization(CloudBackupAuthorizationAction.Download(backupId))

    fun delete(backupId: String) = requestAuthorization(CloudBackupAuthorizationAction.Delete(backupId))

    fun onAuthorizationCancelled() {
        mutableUiState.value = mutableUiState.value.copy(error = null)
    }

    fun onAuthorizationFailed(error: CloudBackupError) {
        if (error == CloudBackupError.AuthorizationExpired) {
            viewModelScope.launch { repository.markNeedsAuthorization() }
        }
        mutableUiState.value = mutableUiState.value.copy(error = error)
    }

    fun onAuthorized(action: CloudBackupAuthorizationAction, accountEmail: String, accessToken: String) {
        viewModelScope.launch {
            when (action) {
                CloudBackupAuthorizationAction.Connect -> connectAccount(accountEmail, accessToken)
                CloudBackupAuthorizationAction.Upload -> uploadBackup(accessToken)
                CloudBackupAuthorizationAction.LoadBackups -> loadBackupList(accessToken)
                is CloudBackupAuthorizationAction.Download -> downloadBackup(accessToken, action.backupId)
                is CloudBackupAuthorizationAction.Delete -> deleteBackup(accessToken, action.backupId)
            }
        }
    }

    fun clearResult() {
        mutableUiState.value = mutableUiState.value.copy(error = null, backupCompleted = false)
    }

    fun disconnected() {
        viewModelScope.launch {
            repository.clearConnection()
            mutableUiState.value = mutableUiState.value.copy(backups = emptyList(), error = null)
        }
    }

    private fun requestAuthorization(action: CloudBackupAuthorizationAction) {
        if (
            mutableUiState.value.isUploading ||
            mutableUiState.value.isLoadingBackups ||
            mutableUiState.value.isDownloading ||
            mutableUiState.value.isDeleting
        ) return
        mutableUiState.value = mutableUiState.value.copy(error = null, backupCompleted = false)
        authorizationChannel.trySend(action)
    }

    private suspend fun connectAccount(accountEmail: String, accessToken: String) {
        repository.rememberConnection(CloudBackupAccount(accountEmail))
        loadBackupList(accessToken)
    }

    private suspend fun uploadBackup(accessToken: String) {
        mutableUiState.value = mutableUiState.value.copy(isUploading = true, error = null)
        runCatching { repository.uploadBackup(accessToken) }
            .onSuccess {
                mutableUiState.value = mutableUiState.value.copy(isUploading = false, backupCompleted = true)
                loadBackupList(accessToken)
            }
            .onFailure { error ->
                error.applyToUi(CloudBackupError.UploadFailed) { mapped ->
                    mutableUiState.value = mutableUiState.value.copy(isUploading = false, error = mapped)
                }
            }
    }

    private suspend fun loadBackupList(accessToken: String) {
        mutableUiState.value = mutableUiState.value.copy(isLoadingBackups = true, error = null)
        runCatching { repository.listBackups(accessToken) }
            .onSuccess { backups ->
                mutableUiState.value = mutableUiState.value.copy(
                    isLoadingBackups = false,
                    backups = CloudBackupOrdering.newestFirst(backups),
                )
            }
            .onFailure { error ->
                error.applyToUi(CloudBackupError.Unknown(error.message)) { mapped ->
                    mutableUiState.value = mutableUiState.value.copy(isLoadingBackups = false, error = mapped)
                }
            }
    }

    private suspend fun downloadBackup(accessToken: String, backupId: String) {
        mutableUiState.value = mutableUiState.value.copy(isDownloading = true, error = null)
        runCatching { repository.downloadBackup(accessToken, backupId) }
            .onSuccess { download ->
                mutableUiState.value = mutableUiState.value.copy(isDownloading = false)
                restoreChannel.send(CloudRestoreReady(download))
            }
            .onFailure { error ->
                error.applyToUi(CloudBackupError.DownloadFailed) { mapped ->
                    mutableUiState.value = mutableUiState.value.copy(isDownloading = false, error = mapped)
                }
            }
    }

    private suspend fun deleteBackup(accessToken: String, backupId: String) {
        mutableUiState.value = mutableUiState.value.copy(isDeleting = true, error = null)
        runCatching { repository.deleteBackup(accessToken, backupId) }
            .onSuccess {
                mutableUiState.value = mutableUiState.value.copy(isDeleting = false)
                loadBackupList(accessToken)
            }
            .onFailure { error ->
                error.applyToUi(CloudBackupError.DeleteFailed) { mapped ->
                    mutableUiState.value = mutableUiState.value.copy(isDeleting = false, error = mapped)
                }
            }
    }

    private fun Throwable.toCloudError(fallback: CloudBackupError): CloudBackupError = when {
        googleDriveStatusCode() == 401 || message?.contains("401") == true -> CloudBackupError.AuthorizationExpired
        this is CloudBackupException && cause?.message?.contains("format is unsupported") == true -> CloudBackupError.UnsupportedFormat
        this is CloudBackupException && cause is IllegalArgumentException -> CloudBackupError.InvalidBackup
        generateSequence(this) { it.cause }.any { it is IOException } -> CloudBackupError.NetworkUnavailable
        else -> fallback
    }

    private fun Throwable.googleDriveStatusCode(): Int? =
        generateSequence(this) { it.cause }
            .filterIsInstance<GoogleDriveHttpException>()
            .firstOrNull()
            ?.statusCode

    private fun Throwable.applyToUi(
        fallback: CloudBackupError,
        update: (CloudBackupError) -> Unit,
    ) {
        val mapped = toCloudError(fallback)
        if (mapped == CloudBackupError.AuthorizationExpired) viewModelScope.launch { repository.markNeedsAuthorization() }
        update(mapped)
    }

    companion object {
        fun factory(repository: CloudBackupRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CloudBackupViewModel(repository) }
        }
    }
}
