package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.repository.RestorePreview
import com.kumo.bubu.domain.repository.RestoreRepository
import com.kumo.bubu.domain.repository.RecoveryBackup
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
}

class RestoreViewModel(
    private val restoreRepository: RestoreRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RestoreUiState())
    val uiState = mutableUiState.asStateFlow()
    private var sourceUriString: String? = null

    init {
        refreshRecoveryBackup()
    }

    fun preview(sourceUriString: String) {
        if (mutableUiState.value.isLoadingPreview || mutableUiState.value.isRestoring) return
        this.sourceUriString = sourceUriString
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isLoadingPreview = true,
                preview = null,
                error = null,
                completed = false,
            )
            runCatching { restoreRepository.preview(sourceUriString) }
                .onSuccess { preview -> mutableUiState.value = mutableUiState.value.copy(preview = preview, isLoadingPreview = false) }
                .onFailure { mutableUiState.value = mutableUiState.value.copy(isLoadingPreview = false, error = RestoreError.INVALID_BACKUP) }
        }
    }

    fun restore() {
        val source = sourceUriString ?: return
        if (mutableUiState.value.preview == null || mutableUiState.value.isRestoring) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isRestoring = true, error = null)
            runCatching { restoreRepository.restore(source) }
                .onSuccess {
                    mutableUiState.value = mutableUiState.value.copy(isRestoring = false, preview = null, completed = true)
                    refreshRecoveryBackup()
                }
                .onFailure { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isRestoring = false,
                        error = RestoreError.RESTORE_FAILED,
                    )
                }
        }
    }

    fun clear() {
        sourceUriString = null
        mutableUiState.value = mutableUiState.value.copy(
            isLoadingPreview = false,
            isRestoring = false,
            preview = null,
            error = null,
            completed = false,
        )
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
        fun factory(restoreRepository: RestoreRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RestoreViewModel(restoreRepository) }
        }
    }
}
