package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.repository.RestorePreview
import com.kumo.bubu.domain.repository.RestoreRepository
import com.kumo.bubu.domain.repository.RecoveryBackup
import com.kumo.bubu.domain.repository.BackupTooLargeException
import com.kumo.bubu.domain.repository.CloudBackupDownload
import com.kumo.bubu.domain.repository.CloudBackupRepository
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RestoreUiState(
    val isLoadingPreview: Boolean = false,
    val isRestoring: Boolean = false,
    val preview: RestorePreview? = null,
    val error: RestoreError? = null,
    val completed: Boolean = false,
    val recoveryBackup: RecoveryBackup? = null,
    val isManagingRecovery: Boolean = false,
    val recoveryActionFailed: Boolean = false,
)

enum class RestoreError {
    INVALID_BACKUP,
    RESTORE_FAILED,
    BACKUP_TOO_LARGE,
}

class RestoreViewModel(
    private val restoreRepository: RestoreRepository,
    private val cloudBackupRepository: CloudBackupRepository? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RestoreUiState())
    val uiState = mutableUiState.asStateFlow()
    private var sourceUriString: String? = null
    private val cloudDownloads = mutableSetOf<CloudBackupDownload>()
    private var previewJob: Job? = null

    init {
        refreshRecoveryBackup()
    }

    fun preview(sourceUriString: String) {
        if (mutableUiState.value.isLoadingPreview || mutableUiState.value.isRestoring) return
        discardCloudDownload()
        startPreview(sourceUriString)
    }

    fun previewCloudDownload(sourceUriString: String, download: CloudBackupDownload) {
        if (mutableUiState.value.isLoadingPreview || mutableUiState.value.isRestoring) {
            cloudDownloads += download
            runCatching { cloudBackupRepository?.discardDownload(download) }
                .onSuccess { cloudDownloads -= download }
                .onFailure { LOGGER.log(Level.WARNING, "Unable to clean a private restore download.", it) }
            return
        }
        discardCloudDownload()
        cloudDownloads += download
        startPreview(sourceUriString)
    }

    private fun startPreview(sourceUriString: String) {
        this.sourceUriString = sourceUriString
        previewJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isLoadingPreview = true,
                preview = null,
                error = null,
                completed = false,
            )
            runCatching { restoreRepository.preview(sourceUriString) }
                .onSuccess { preview -> mutableUiState.value = mutableUiState.value.copy(preview = preview, isLoadingPreview = false) }
                .onFailure {
                    if (it is CancellationException) throw it
                    discardCloudDownload()
                    mutableUiState.value = mutableUiState.value.copy(isLoadingPreview = false, error = RestoreError.INVALID_BACKUP)
                }
        }
    }

    fun restore() {
        val source = sourceUriString ?: return
        if (mutableUiState.value.preview == null || mutableUiState.value.isRestoring) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isRestoring = true, error = null)
            runCatching { restoreRepository.restore(source) }
                .onSuccess {
                    discardCloudDownload()
                    sourceUriString = null
                    mutableUiState.value = mutableUiState.value.copy(isRestoring = false, preview = null, completed = true)
                    refreshRecoveryBackup()
                }
                .onFailure { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isRestoring = false,
                        error = if (error is BackupTooLargeException || error.cause is BackupTooLargeException) {
                            RestoreError.BACKUP_TOO_LARGE
                        } else RestoreError.RESTORE_FAILED,
                    )
                }
        }
    }

    fun clear() {
        if (mutableUiState.value.isRestoring) return
        previewJob?.cancel()
        discardCloudDownload()
        sourceUriString = null
        mutableUiState.value = mutableUiState.value.copy(
            isLoadingPreview = false,
            isRestoring = false,
            preview = null,
            error = null,
            completed = false,
        )
    }

    private fun discardCloudDownload() {
        cloudDownloads.toList().forEach { download ->
            runCatching { cloudBackupRepository?.discardDownload(download) }
                .onSuccess { cloudDownloads -= download }
                .onFailure { LOGGER.log(Level.WARNING, "Unable to clean a private restore download.", it) }
        }
    }

    override fun onCleared() {
        discardCloudDownload()
    }

    fun refreshRecoveryBackup() {
        viewModelScope.launch {
            val recovery = runCatching { restoreRepository.getLatestRecoveryBackup() }.getOrNull()
            mutableUiState.value = mutableUiState.value.copy(recoveryBackup = recovery)
        }
    }

    fun exportRecoveryBackup(destinationUriString: String) {
        if (mutableUiState.value.isManagingRecovery) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isManagingRecovery = true, recoveryActionFailed = false)
            runCatching { restoreRepository.exportLatestRecoveryBackup(destinationUriString) }
                .onSuccess { backup -> mutableUiState.value = mutableUiState.value.copy(isManagingRecovery = false, recoveryBackup = backup) }
                .onFailure { mutableUiState.value = mutableUiState.value.copy(isManagingRecovery = false, recoveryActionFailed = true) }
        }
    }

    fun deleteRecoveryBackup() {
        if (mutableUiState.value.isManagingRecovery) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isManagingRecovery = true, recoveryActionFailed = false)
            runCatching { restoreRepository.deleteLatestRecoveryBackup() }
                .onSuccess { mutableUiState.value = mutableUiState.value.copy(isManagingRecovery = false, recoveryBackup = null) }
                .onFailure { mutableUiState.value = mutableUiState.value.copy(isManagingRecovery = false, recoveryActionFailed = true) }
        }
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(RestoreViewModel::class.java.name)
        fun factory(restoreRepository: RestoreRepository, cloudBackupRepository: CloudBackupRepository? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer { RestoreViewModel(restoreRepository, cloudBackupRepository) }
        }
    }
}
