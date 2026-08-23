package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.core.notification.BackupReminderScheduler
import com.kumo.bubu.domain.repository.BackupReminderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class BackupReminderUiState(
    val isLoading: Boolean = true,
    val enabled: Boolean = false,
    val isSaving: Boolean = false,
    val hasError: Boolean = false,
)

class BackupReminderViewModel(
    private val settings: BackupReminderSettings,
    private val scheduler: BackupReminderScheduler,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackupReminderUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.observeEnabled().collectLatest { enabled ->
                mutableUiState.value = mutableUiState.value.copy(isLoading = false, enabled = enabled)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (mutableUiState.value.isSaving) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isSaving = true, hasError = false)
            runCatching { settings.setEnabled(enabled) }
                .onSuccess {
                    if (enabled) scheduler.scheduleDailyCheck() else scheduler.cancelDailyCheck()
                    mutableUiState.value = mutableUiState.value.copy(isSaving = false)
                }
                .onFailure { mutableUiState.value = mutableUiState.value.copy(isSaving = false, hasError = true) }
        }
    }

    companion object {
        fun factory(
            settings: BackupReminderSettings,
            scheduler: BackupReminderScheduler,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupReminderViewModel(settings, scheduler) }
        }
    }
}
