package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.taiwanStatutoryRuleVerifiedDate
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.StatutoryReminderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatutoryReminderSettingsUiState(
    val taxAndFeeEnabled: Boolean = true,
    val verifiedDate: String = taiwanStatutoryRuleVerifiedDate.toString(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasError: Boolean = false,
)

class StatutoryReminderSettingsViewModel(
    settings: StatutoryReminderSettings,
    private val reminderRepository: ReminderRepository,
) : ViewModel() {
    private val isSaving = MutableStateFlow(false)
    private val hasError = MutableStateFlow(false)

    val uiState = combine(
        settings.observeTaxAndFeeEnabled(),
        isSaving,
        hasError,
    ) { enabled, saving, failed ->
        StatutoryReminderSettingsUiState(
            taxAndFeeEnabled = enabled,
            isLoading = false,
            isSaving = saving,
            hasError = failed,
        )
    }.catch {
        emit(StatutoryReminderSettingsUiState(isLoading = false, hasError = true))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatutoryReminderSettingsUiState(),
    )

    fun setTaxAndFeeEnabled(enabled: Boolean) {
        if (isSaving.value) return
        viewModelScope.launch {
            isSaving.value = true
            runCatching { reminderRepository.setTaxAndFeeRemindersEnabled(enabled) }
                .onSuccess { hasError.value = false }
                .onFailure { hasError.value = true }
            isSaving.value = false
        }
    }

    companion object {
        fun factory(
            settings: StatutoryReminderSettings,
            reminderRepository: ReminderRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatutoryReminderSettingsViewModel(settings, reminderRepository) }
        }
    }
}
