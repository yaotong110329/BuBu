package com.kumo.bubu.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.VehicleReminder
import com.kumo.bubu.domain.model.status
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import com.kumo.bubu.domain.repository.ReminderNotificationSettings
import com.kumo.bubu.core.notification.ReminderNotificationScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderVehicleOption(val id: Long, val name: String)

data class ReminderRow(
    val reminder: VehicleReminder,
    val vehicleName: String,
    val currentOdometerKm: Long,
    val status: ReminderStatus?,
)

data class RemindersUiState(
    val vehicles: List<ReminderVehicleOption> = emptyList(),
    val rows: List<ReminderRow> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val actionFailed: Boolean = false,
    val notificationsEnabled: Boolean = false,
)

sealed interface ReminderEvent {
    data class CreateManual(
        val vehicleId: Long,
        val title: String,
        val dueOdometerText: String,
        val dueDateText: String,
    ) : ReminderEvent

    data class Complete(val id: Long) : ReminderEvent
    data class Snooze(val id: Long, val days: Long) : ReminderEvent
    data class SnoozeUntil(val id: Long, val dateText: String) : ReminderEvent
    data class SetNotificationsEnabled(val enabled: Boolean) : ReminderEvent
    data class SetReminderEnabled(val id: Long, val enabled: Boolean) : ReminderEvent
}

class RemindersViewModel(
    private val reminderRepository: ReminderRepository,
    vehicleRepository: VehicleRepository,
    private val notificationSettings: ReminderNotificationSettings,
    private val notificationScheduler: ReminderNotificationScheduler,
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val actionFailed = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            runCatching { reminderRepository.refreshAutomaticReminders(todayProvider()) }
                .onFailure { actionFailed.value = true }
        }
    }

    val uiState = combine(
        reminderRepository.observeReminders(),
        vehicleRepository.observeVehicles(),
        actionFailed,
        notificationSettings.observeEnabled(),
    ) { reminders, vehicles, failed, notificationsEnabled ->
        val activeVehicles = vehicles.filterNot { it.isArchived }
        val vehiclesById = activeVehicles.associateBy { it.id }
        val rows = reminders.mapNotNull { reminder ->
            vehiclesById[reminder.vehicleId]?.let { vehicle ->
                ReminderRow(
                    reminder = reminder,
                    vehicleName = vehicle.name,
                    currentOdometerKm = vehicle.currentOdometerKm,
                    status = reminder.status(vehicle.currentOdometerKm, todayProvider()),
                )
            }
        }.sortedWith(
            compareBy<ReminderRow> { it.reminder.isCompleted }
                .thenByDescending { it.status?.severity ?: -1 }
                .thenBy { it.reminder.dueDateEpochDay ?: Long.MAX_VALUE }
                .thenBy { it.reminder.dueOdometerKm ?: Long.MAX_VALUE }
                .thenBy { it.reminder.id },
        )
        RemindersUiState(
            vehicles = activeVehicles.map { ReminderVehicleOption(it.id, it.name) },
            rows = rows,
            isLoading = false,
            actionFailed = failed,
            notificationsEnabled = notificationsEnabled,
        )
    }.catch {
        emit(RemindersUiState(isLoading = false, loadFailed = true))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RemindersUiState(),
    )

    fun onEvent(event: ReminderEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is ReminderEvent.CreateManual -> reminderRepository.createManualReminder(
                        ManualReminderInput(
                            vehicleId = event.vehicleId,
                            title = event.title,
                            dueOdometerKm = event.dueOdometerText.trim().takeIf(String::isNotEmpty)?.toLong(),
                            dueDateEpochDay = event.dueDateText.trim().takeIf(String::isNotEmpty)
                                ?.let(LocalDate::parse)
                                ?.toEpochDay(),
                        ),
                    )

                    is ReminderEvent.Complete -> {
                        reminderRepository.completeReminder(event.id)
                        notificationScheduler.cancelReminderNotification(event.id)
                    }
                    is ReminderEvent.Snooze -> {
                        reminderRepository.snoozeReminder(
                            event.id,
                            todayProvider().plusDays(event.days).toEpochDay(),
                        )
                        notificationScheduler.cancelReminderNotification(event.id)
                    }

                    is ReminderEvent.SnoozeUntil -> {
                        reminderRepository.snoozeReminder(
                            event.id,
                            LocalDate.parse(event.dateText.trim()).toEpochDay(),
                        )
                        notificationScheduler.cancelReminderNotification(event.id)
                    }

                    is ReminderEvent.SetNotificationsEnabled -> {
                        notificationSettings.setEnabled(event.enabled)
                        if (event.enabled) {
                            notificationScheduler.scheduleDailyCheck()
                        } else {
                            notificationScheduler.cancelDailyCheck()
                            notificationScheduler.cancelAllReminderNotifications()
                        }
                    }

                    is ReminderEvent.SetReminderEnabled -> {
                        reminderRepository.setReminderEnabled(event.id, event.enabled)
                        if (!event.enabled) notificationScheduler.cancelReminderNotification(event.id)
                    }
                }
                actionFailed.value = false
            } catch (_: Throwable) {
                actionFailed.value = true
            }
        }
    }

    companion object {
        fun factory(
            reminderRepository: ReminderRepository,
            vehicleRepository: VehicleRepository,
            notificationSettings: ReminderNotificationSettings,
            notificationScheduler: ReminderNotificationScheduler,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RemindersViewModel(
                    reminderRepository,
                    vehicleRepository,
                    notificationSettings,
                    notificationScheduler,
                )
            }
        }
    }
}

private val ReminderStatus.severity: Int
    get() = when (this) {
        ReminderStatus.NORMAL -> 0
        ReminderStatus.DUE_SOON -> 1
        ReminderStatus.OVERDUE -> 2
    }
