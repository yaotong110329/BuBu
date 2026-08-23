package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val isCreating: Boolean = false,
    val createdFileName: String? = null,
    val hasError: Boolean = false,
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
                .onFailure { mutableUiState.value = BackupUiState(hasError = true) }
        }
    }

    companion object {
        fun factory(backupRepository: BackupRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(backupRepository) }
        }
    }
}
