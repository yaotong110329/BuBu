package com.kumo.bubu.feature.settings

import androidx.activity.result.IntentSenderRequest
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
import com.kumo.bubu.domain.repository.CloudBackupRetentionException
import com.kumo.bubu.domain.repository.CloudBackupRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

data class CloudBackupUiState(
    val connection: CloudBackupConnection = CloudBackupConnection.NotConnected,
    val backups: List<CloudBackup> = emptyList(),
    val isLoadingBackups: Boolean = false,
    val isUploading: Boolean = false,
    val isDownloading: Boolean = false,
    val isDeleting: Boolean = false,
    val error: CloudBackupError? = null,
    val backupCompleted: Boolean = false,
    val isAuthorizing: Boolean = false,
    val authorizationRequestId: Long? = null,
)

sealed interface CloudBackupAuthorizationAction {
    data object Connect : CloudBackupAuthorizationAction
    data object Upload : CloudBackupAuthorizationAction
    data object LoadBackups : CloudBackupAuthorizationAction
    data class Download(val backupId: String) : CloudBackupAuthorizationAction
    data class Delete(val backupId: String) : CloudBackupAuthorizationAction
}

data class CloudRestoreReady(val download: CloudBackupDownload)

data class CloudAuthorizationRequest(
    val id: Long,
    val action: CloudBackupAuthorizationAction,
    val account: GoogleDriveAccount? = null,
)

data class CloudAuthorizationResolution(val requestId: Long, val request: IntentSenderRequest)

class CloudBackupViewModel(
    private val repository: CloudBackupRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CloudBackupUiState())
    val uiState = mutableUiState.asStateFlow()

    private var operationJob: Job? = null
    private val mutableResolution = MutableStateFlow<CloudAuthorizationResolution?>(null)
    val resolution = mutableResolution.asStateFlow()
    private var nextAuthorizationId = 0L
    private var pendingAuthorization: CloudAuthorizationRequest? = null
    private val authorizationChannel = Channel<CloudAuthorizationRequest>(Channel.BUFFERED)
    val authorizationRequests = authorizationChannel.receiveAsFlow()

    private val restoreChannel = Channel<CloudRestoreReady>(
        Channel.BUFFERED,
        onUndeliveredElement = { request ->
            runCatching { repository.discardDownload(request.download) }
                .onFailure { LOGGER.log(Level.WARNING, "Unable to clean an undelivered restore download.", it) }
        },
    )
    val restoreRequests = restoreChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeConnection().collect { connection ->
                val state = mutableUiState.value
                val previousEmail = (state.connection as? CloudBackupConnection.Connected)?.account?.email
                val currentEmail = (connection as? CloudBackupConnection.Connected)?.account?.email
                val accountChanged = previousEmail != null && !previousEmail.equals(currentEmail, ignoreCase = true)
                mutableUiState.value = state.copy(
                    connection = connection,
                    backups = if (accountChanged) emptyList() else state.backups,
                    backupCompleted = if (accountChanged) false else state.backupCompleted,
                )
            }
        }
    }

    fun connect() = requestAuthorization(CloudBackupAuthorizationAction.Connect)

    fun upload() = requestAuthorization(CloudBackupAuthorizationAction.Upload)

    fun loadBackups() = requestAuthorization(CloudBackupAuthorizationAction.LoadBackups)

    fun download(backupId: String) = requestAuthorization(CloudBackupAuthorizationAction.Download(backupId))

    fun delete(backupId: String) = requestAuthorization(CloudBackupAuthorizationAction.Delete(backupId))

    fun onAuthorizationCancelled() {
        pendingAuthorization = null
        mutableResolution.value = null
        mutableUiState.value = mutableUiState.value.copy(error = null, isAuthorizing = false, authorizationRequestId = null)
    }

    fun isPendingAuthorization(requestId: Long): Boolean = pendingAuthorization?.id == requestId

    fun onResolutionReady(requestId: Long, request: IntentSenderRequest) {
        if (isPendingAuthorization(requestId)) mutableResolution.value = CloudAuthorizationResolution(requestId, request)
    }

    fun consumeResolution(requestId: Long): IntentSenderRequest? {
        val ready = mutableResolution.value?.takeIf { it.requestId == requestId } ?: return null
        mutableResolution.value = null
        return ready.request
    }

    fun leaveSettings() {
        onAuthorizationCancelled()
        operationJob?.cancel()
        while (true) {
            val download = restoreChannel.tryReceive().getOrNull()?.download ?: break
            runCatching { repository.discardDownload(download) }
                .onFailure { LOGGER.log(Level.WARNING, "Unable to clean an abandoned restore download.", it) }
        }
        mutableUiState.value = mutableUiState.value.copy(isUploading = false, isLoadingBackups = false, isDownloading = false, isDeleting = false)
    }

    fun onAccountSelected(requestId: Long, account: GoogleDriveAccount) {
        pendingAuthorization?.takeIf { it.id == requestId }?.let {
            pendingAuthorization = it.copy(account = account)
        }
    }

    fun consumeAuthorization(requestId: Long): CloudAuthorizationRequest? {
        val request = pendingAuthorization?.takeIf { it.id == requestId } ?: return null
        pendingAuthorization = null
        mutableResolution.value = null
        mutableUiState.value = mutableUiState.value.copy(isAuthorizing = false, authorizationRequestId = null)
        return request
    }

    fun consumeAuthorizationResult(requestId: Long): CloudAuthorizationRequest? {
        val request = pendingAuthorization?.takeIf { it.id == requestId && it.account != null } ?: return null
        return consumeAuthorization(request.id)
    }

    fun onAuthorizationFailed(error: CloudBackupError) {
        pendingAuthorization = null
        mutableResolution.value = null
        if (error == CloudBackupError.AuthorizationExpired) {
            viewModelScope.launch { repository.markNeedsAuthorization() }
        }
        mutableUiState.value = mutableUiState.value.copy(error = error, isAuthorizing = false, authorizationRequestId = null)
    }

    fun onAuthorized(action: CloudBackupAuthorizationAction, accountEmail: String, accessToken: String) {
        pendingAuthorization = null
        mutableResolution.value = null
        mutableUiState.value = mutableUiState.value.copy(isAuthorizing = false, authorizationRequestId = null)
        operationJob = viewModelScope.launch {
            if (action != CloudBackupAuthorizationAction.Connect) {
                val authorizationError = when (val connection = repository.observeConnection().first()) {
                    is CloudBackupConnection.Connected -> if (
                        connection.account.email.equals(accountEmail, ignoreCase = true)
                    ) null else CloudBackupError.AccountMismatch
                    CloudBackupConnection.NeedsAuthorization -> CloudBackupError.AuthorizationExpired
                    CloudBackupConnection.NotConnected -> CloudBackupError.NotConnected
                }
                if (authorizationError != null) {
                    mutableUiState.value = mutableUiState.value.copy(error = authorizationError)
                    return@launch
                }
            }
            val selectionError = selectionError(action)
            if (selectionError != null) {
                mutableUiState.value = mutableUiState.value.copy(error = selectionError)
                return@launch
            }
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
            pendingAuthorization != null ||
            mutableUiState.value.isUploading ||
            mutableUiState.value.isLoadingBackups ||
            mutableUiState.value.isDownloading ||
            mutableUiState.value.isDeleting
        ) return
        val selectionError = selectionError(action)
        if (selectionError != null) {
            mutableUiState.value = mutableUiState.value.copy(error = selectionError)
            return
        }
        val request = CloudAuthorizationRequest(++nextAuthorizationId, action)
        pendingAuthorization = request
        mutableUiState.value = mutableUiState.value.copy(error = null, backupCompleted = false, isAuthorizing = true, authorizationRequestId = request.id)
        authorizationChannel.trySend(request)
    }

    private fun selectionError(action: CloudBackupAuthorizationAction): CloudBackupError? {
        val selectedBackupId = when (action) {
            is CloudBackupAuthorizationAction.Download -> action.backupId
            is CloudBackupAuthorizationAction.Delete -> action.backupId
            else -> return null
        }
        if (mutableUiState.value.backups.any { it.id == selectedBackupId }) return null
        return if (action is CloudBackupAuthorizationAction.Download) {
            CloudBackupError.DownloadFailed
        } else CloudBackupError.DeleteFailed
    }

    private suspend fun connectAccount(accountEmail: String, accessToken: String) {
        mutableUiState.value = mutableUiState.value.copy(backups = emptyList())
        repository.rememberConnection(CloudBackupAccount(accountEmail))
        mutableUiState.value = mutableUiState.value.copy(connection = repository.observeConnection().first())
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
                if (error is CloudBackupRetentionException) {
                    mutableUiState.value = mutableUiState.value.copy(isUploading = false, backupCompleted = true)
                    loadBackupList(accessToken)
                    error.applyToUi(CloudBackupError.RetentionIncomplete) {
                        mutableUiState.value = mutableUiState.value.copy(error = CloudBackupError.RetentionIncomplete)
                    }
                } else {
                    error.applyToUi(CloudBackupError.UploadFailed) { mapped ->
                        mutableUiState.value = mutableUiState.value.copy(isUploading = false, error = mapped)
                    }
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
        generateSequence(this) { it.cause }.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
        val mapped = toCloudError(fallback)
        if (mapped == CloudBackupError.AuthorizationExpired) viewModelScope.launch { repository.markNeedsAuthorization() }
        update(mapped)
    }

    override fun onCleared() {
        pendingAuthorization = null
        authorizationChannel.cancel()
        restoreChannel.cancel()
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(CloudBackupViewModel::class.java.name)
        fun factory(repository: CloudBackupRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CloudBackupViewModel(repository) }
        }
    }
}
