package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.repository.BackupRepository
import com.kumo.bubu.domain.repository.BackupTooLargeException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val isCreating: Boolean = false,
    val createdFileName: String? = null,
    val hasError: Boolean = false,
    val isTooLarge: Boolean = false,
)

class BackupViewModel(
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackupUiState())
    val uiState = mutableUiState.asStateFlow()

    fun clearResult() {
        mutableUiState.value = BackupUiState()
    }

    fun createBackup(destinationUriString: String) {
        if (mutableUiState.value.isCreating) return
        viewModelScope.launch {
            mutableUiState.value = BackupUiState(isCreating = true)
            runCatching { backupRepository.createBackup(destinationUriString) }
                .onSuccess { result -> mutableUiState.value = BackupUiState(createdFileName = result.fileName) }
                .onFailure { error ->
                    mutableUiState.value = BackupUiState(hasError = true, isTooLarge = error is BackupTooLargeException)
                }
        }
    }

    companion object {
        fun factory(backupRepository: BackupRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(backupRepository) }
        }
    }
}
