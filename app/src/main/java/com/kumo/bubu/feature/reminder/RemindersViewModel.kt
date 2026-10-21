package com.kumo.bubu.feature.reminder

import androidx.lifecycle.ViewModel
import com.kumo.bubu.core.state.LocalDateState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.DerivedMaintenanceReminder
import com.kumo.bubu.domain.model.MaintenanceReminderStatus
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.VehicleReminder
import com.kumo.bubu.domain.model.status
import com.kumo.bubu.domain.repository.ReminderRepository
import com.kumo.bubu.domain.repository.ServiceRepository
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

data class MaintenanceReminderRow(
    val reminder: DerivedMaintenanceReminder,
    val vehicleName: String,
)

private data class ReminderInputs(
    val reminders: List<VehicleReminder>,
    val vehicles: List<com.kumo.bubu.domain.model.Vehicle>,
    val actionFailed: Boolean,
    val notificationsEnabled: Boolean,
)

data class RemindersUiState(
    val vehicles: List<ReminderVehicleOption> = emptyList(),
    val selectedVehicleId: Long? = null,
    val rows: List<ReminderRow> = emptyList(),
    val maintenanceRows: List<MaintenanceReminderRow> = emptyList(),
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
    data class SelectVehicle(val vehicleId: Long) : ReminderEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(
    private val reminderRepository: ReminderRepository,
    private val serviceRepository: ServiceRepository,
    vehicleRepository: VehicleRepository,
    private val notificationSettings: ReminderNotificationSettings,
    private val notificationScheduler: ReminderNotificationScheduler,
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val dateState = LocalDateState(viewModelScope, todayProvider)
    private val actionFailed = MutableStateFlow(false)
    private val selectedVehicleId = MutableStateFlow<Long?>(null)
    private val focusedReminderId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            runCatching { reminderRepository.refreshAutomaticReminders(todayProvider()) }
                .onFailure { actionFailed.value = true }
        }
    }

    val uiState = combine(
        combine(
            reminderRepository.observeReminders(),
            vehicleRepository.observeVehicles(),
            actionFailed,
            notificationSettings.observeEnabled(),
        ) { reminders, vehicles, failed, notificationsEnabled ->
            ReminderInputs(reminders, vehicles, failed, notificationsEnabled)
        },
        selectedVehicleId,
    ) { inputs, selectedId -> inputs to selectedId }.let { inputsWithSelectedVehicle -> combine(
        inputsWithSelectedVehicle,
        dateState.dates.flatMapLatest { today ->
            serviceRepository.observeMaintenanceReminders(today).map { today to it }
        },
        focusedReminderId,
    ) { (inputs, requestedVehicleId), (today, maintenanceReminders), focusedId ->
        val (reminders, vehicles, failed, notificationsEnabled) = inputs
        val activeVehicles = vehicles.filterNot { it.isArchived }
        val targetVehicleId = reminders.firstOrNull { it.id == focusedId }?.vehicleId
        val selectedId = (requestedVehicleId ?: targetVehicleId)?.takeIf { requested -> activeVehicles.any { it.id == requested } }
            ?: activeVehicles.firstOrNull()?.id
        val vehiclesById = activeVehicles.associateBy { it.id }
        val rows = reminders.filter { it.vehicleId == selectedId }.mapNotNull { reminder ->
            vehiclesById[reminder.vehicleId]?.let { vehicle ->
                ReminderRow(
                    reminder = reminder,
                    vehicleName = vehicle.name,
                    currentOdometerKm = vehicle.currentOdometerKm,
                    status = reminder.status(vehicle.currentOdometerKm, today),
                )
            }
        }.sortedWith(
            compareBy<ReminderRow> { it.reminder.isCompleted }
                .thenByDescending { it.status?.severity ?: -1 }
                .thenBy { it.reminder.dueDateEpochDay ?: Long.MAX_VALUE }
                .thenBy { it.reminder.dueOdometerKm ?: Long.MAX_VALUE }
                .thenBy { it.reminder.id },
        )
        val maintenanceRows = maintenanceReminders.filter { it.vehicleId == selectedId }.mapNotNull { reminder ->
            vehiclesById[reminder.vehicleId]?.let { vehicle ->
                MaintenanceReminderRow(reminder = reminder, vehicleName = vehicle.name)
            }
        }.sortedWith(
            compareByDescending<MaintenanceReminderRow> { it.reminder.calculation.status.maintenanceSeverity }
                .thenBy { it.reminder.sortOrder }
                .thenBy { it.reminder.serviceTypeName },
        )
        RemindersUiState(
            vehicles = activeVehicles.map { ReminderVehicleOption(it.id, it.name) },
            selectedVehicleId = selectedId,
            rows = rows,
            maintenanceRows = maintenanceRows,
            isLoading = false,
            actionFailed = failed,
            notificationsEnabled = notificationsEnabled,
        )
    } }.catch {
        emit(RemindersUiState(isLoading = false, loadFailed = true))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RemindersUiState(),
    )

    fun focusReminder(id: Long) {
        focusedReminderId.value = id
        selectedVehicleId.value = null
    }

    fun onForeground() = dateState.onForeground()

    fun onBackground() = dateState.onBackground()

    fun onEvent(event: ReminderEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is ReminderEvent.SelectVehicle -> selectedVehicleId.value = event.vehicleId
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
            serviceRepository: ServiceRepository,
            vehicleRepository: VehicleRepository,
            notificationSettings: ReminderNotificationSettings,
            notificationScheduler: ReminderNotificationScheduler,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RemindersViewModel(
                    reminderRepository,
                    serviceRepository,
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

private val MaintenanceReminderStatus.maintenanceSeverity: Int
    get() = when (this) {
        MaintenanceReminderStatus.UNKNOWN -> 0
        MaintenanceReminderStatus.NORMAL -> 1
        MaintenanceReminderStatus.UPCOMING -> 2
        MaintenanceReminderStatus.DUE -> 3
        MaintenanceReminderStatus.OVERDUE -> 4
    }
