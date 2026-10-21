package com.kumo.bubu.feature.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.MaintenanceReminderType
import com.kumo.bubu.domain.model.MaintenanceReminderStatus
import java.time.LocalDate

@Composable
fun RemindersRoute(
    viewModel: RemindersViewModel,
    onBack: (() -> Unit)? = null,
    onCreateMaintenance: (Long, Long) -> Unit = { _, _ -> },
    highlightedReminderId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onForeground()
        onPauseOrDispose { viewModel.onBackground() }
    }
    LaunchedEffect(viewModel, highlightedReminderId) {
        highlightedReminderId?.let(viewModel::focusReminder)
    }
    RemindersScreen(state, viewModel::onEvent, onBack, onCreateMaintenance, highlightedReminderId, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onEvent: (ReminderEvent) -> Unit,
    onBack: (() -> Unit)? = null,
    onCreateMaintenance: (Long, Long) -> Unit = { _, _ -> },
    highlightedReminderId: Long? = null,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var customSnoozeReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMaintenance by remember { mutableStateOf<MaintenanceReminderRow?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    onBack?.let { navigateBack ->
                        IconButton(onClick = navigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.vehicles.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier.testTag("reminders-add"),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.reminders_add))
                }
            }
        },
    ) { padding ->
        ReminderContent(
            state = state,
            onEvent = onEvent,
            onRequestCustomSnooze = { reminderId -> customSnoozeReminderId = reminderId },
            onSelectMaintenance = { selectedMaintenance = it },
            highlightedReminderId = highlightedReminderId,
            padding = padding,
        )
    }
    if (showAddSheet) {
        AddManualReminderSheet(
            vehicles = state.vehicles,
            onDismiss = { showAddSheet = false },
            onSave = { vehicleId, title, dueOdometer, dueDate ->
                onEvent(ReminderEvent.CreateManual(vehicleId, title, dueOdometer, dueDate))
                showAddSheet = false
            },
        )
    }
    customSnoozeReminderId?.let { reminderId ->
        SnoozeUntilDialog(
            onDismiss = { customSnoozeReminderId = null },
            onConfirm = { dateText ->
                onEvent(ReminderEvent.SnoozeUntil(reminderId, dateText))
                customSnoozeReminderId = null
            },
        )
    }
    selectedMaintenance?.let { row ->
        MaintenanceReminderDetailSheet(
            row = row,
            onDismiss = { selectedMaintenance = null },
            onCreateMaintenance = {
                selectedMaintenance = null
                onCreateMaintenance(row.reminder.vehicleId, row.reminder.serviceTypeId)
            },
        )
    }
}

private sealed interface ReminderListEntry {
    val key: String

    data object NotificationSettings : ReminderListEntry { override val key = "notification-settings" }
    data object VehicleSelector : ReminderListEntry { override val key = "vehicle-selector" }
    data class Message(val stringRes: Int) : ReminderListEntry { override val key = "message-$stringRes" }
    data object MaintenanceSummary : ReminderListEntry { override val key = "maintenance-summary" }
    data object MaintenanceHeading : ReminderListEntry { override val key = "maintenance-heading" }
    data class Maintenance(val row: MaintenanceReminderRow) : ReminderListEntry {
        override val key = "maintenance-${row.reminder.vehicleId}-${row.reminder.serviceTypeId}"
    }
    data class Reminder(val row: ReminderRow) : ReminderListEntry {
        override val key = "reminder-${row.reminder.id}"
    }
}

@Composable
private fun ReminderContent(
    state: RemindersUiState,
    onEvent: (ReminderEvent) -> Unit,
    onRequestCustomSnooze: (Long) -> Unit,
    onSelectMaintenance: (MaintenanceReminderRow) -> Unit,
    highlightedReminderId: Long?,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onEvent(ReminderEvent.SetNotificationsEnabled(granted))
    }
    val entries = buildList<ReminderListEntry> {
        add(ReminderListEntry.NotificationSettings)
        when {
            state.isLoading -> add(ReminderListEntry.Message(R.string.reminders_loading))
            state.loadFailed -> add(ReminderListEntry.Message(R.string.reminders_load_error))
            state.vehicles.isEmpty() -> add(ReminderListEntry.Message(R.string.reminders_no_vehicles))
            state.rows.isEmpty() && state.maintenanceRows.isEmpty() -> add(ReminderListEntry.Message(R.string.reminders_empty))
            else -> {
                add(ReminderListEntry.VehicleSelector)
                if (state.actionFailed) add(ReminderListEntry.Message(R.string.reminders_action_error))
                if (state.maintenanceRows.isNotEmpty()) {
                    add(ReminderListEntry.MaintenanceSummary)
                    add(ReminderListEntry.MaintenanceHeading)
                    state.maintenanceRows.forEach { add(ReminderListEntry.Maintenance(it)) }
                }
                state.rows.forEach { add(ReminderListEntry.Reminder(it)) }
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedReminderId, entries) {
        val reminderId = highlightedReminderId ?: return@LaunchedEffect
        val targetIndex = entries.indexOfFirst { entry ->
            entry is ReminderListEntry.Reminder && entry.row.reminder.id == reminderId
        }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("reminders-scroll-list"),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = ReminderListEntry::key) { entry ->
            when (entry) {
                ReminderListEntry.NotificationSettings -> NotificationSettingsCard(
                    enabled = state.notificationsEnabled,
                    onEnabledChange = { enabled ->
                        when {
                            !enabled -> onEvent(ReminderEvent.SetNotificationsEnabled(false))
                            notificationsAllowed -> onEvent(ReminderEvent.SetNotificationsEnabled(true))
                            else -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                ReminderListEntry.VehicleSelector -> ReminderVehicleSelector(state, onEvent)
                is ReminderListEntry.Message -> ReminderMessage(entry.stringRes)
                ReminderListEntry.MaintenanceSummary -> MaintenanceReminderSummary(state.maintenanceRows)
                ReminderListEntry.MaintenanceHeading -> Text(
                    stringResource(R.string.maintenance_reminders_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
                is ReminderListEntry.Maintenance -> MaintenanceReminderCard(entry.row, onClick = { onSelectMaintenance(entry.row) })
                is ReminderListEntry.Reminder -> ReminderCard(
                    entry.row, onEvent, onRequestCustomSnooze,
                    highlighted = entry.row.reminder.id == highlightedReminderId,
                )
            }
        }
    }
}

@Composable
private fun ReminderVehicleSelector(state: RemindersUiState, onEvent: (ReminderEvent) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("reminders-vehicle-selector"),
    ) {
        items(state.vehicles, key = ReminderVehicleOption::id) { vehicle ->
            FilterChip(
                selected = state.selectedVehicleId == vehicle.id,
                onClick = { onEvent(ReminderEvent.SelectVehicle(vehicle.id)) },
                label = { Text(vehicle.name) },
                modifier = Modifier.testTag("reminders-vehicle-${vehicle.id}"),
            )
        }
    }
}

@Composable
private fun MaintenanceReminderSummary(rows: List<MaintenanceReminderRow>) {
    val dueCount = rows.count {
        it.reminder.calculation.status == MaintenanceReminderStatus.DUE ||
            it.reminder.calculation.status == MaintenanceReminderStatus.OVERDUE
    }
    val upcomingCount = rows.count { it.reminder.calculation.status == MaintenanceReminderStatus.UPCOMING }
    val unknownCount = rows.count { it.reminder.calculation.status == MaintenanceReminderStatus.UNKNOWN }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.maintenance_reminders_heading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.maintenance_reminder_summary_due, dueCount))
            Text(stringResource(R.string.maintenance_reminder_summary_upcoming, upcomingCount))
            Text(stringResource(R.string.maintenance_reminder_summary_unknown, unknownCount))
        }
    }
}

@Composable
private fun MaintenanceReminderCard(row: MaintenanceReminderRow, onClick: () -> Unit) {
    val reminder = row.reminder
    val calculation = reminder.calculation
    Card(
        modifier = Modifier.fillMaxWidth().testTag("maintenance-reminder-${reminder.vehicleId}-${reminder.serviceTypeId}").clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = { Text(reminder.serviceTypeName, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.vehicleName)
                    val baseline = reminder.baseline
                    if (baseline.lastServiceMileage == null && baseline.lastServiceDate == null) {
                        Text(stringResource(R.string.maintenance_reminder_unknown))
                    } else if (reminder.usesConfiguredMileageBaseline) {
                        Text(
                            stringResource(
                                R.string.maintenance_reminder_configured_baseline,
                                requireNotNull(baseline.lastServiceMileage),
                            ),
                        )
                    } else {
                        Text(
                            stringResource(
                                R.string.maintenance_reminder_baseline,
                                baseline.lastServiceMileage?.toString() ?: stringResource(R.string.maintenance_reminder_not_recorded),
                                baseline.lastServiceDate?.toString() ?: stringResource(R.string.maintenance_reminder_not_recorded),
                            ),
                        )
                    }
                    calculation.nextMileage?.let { Text(stringResource(R.string.maintenance_reminder_next_mileage, it)) }
                    calculation.nextDate?.let { Text(stringResource(R.string.maintenance_reminder_next_date, it.toString())) }
                }
            },
            trailingContent = { MaintenanceStatusChip(calculation.status, reminder.reminderType) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceReminderDetailSheet(
    row: MaintenanceReminderRow,
    onDismiss: () -> Unit,
    onCreateMaintenance: () -> Unit,
) {
    val reminder = row.reminder
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(reminder.serviceTypeName, style = MaterialTheme.typography.titleLarge)
            Text(row.vehicleName)
            Text(
                stringResource(
                    R.string.maintenance_reminder_interval,
                    reminder.intervalMileage?.toString() ?: stringResource(R.string.maintenance_reminder_not_recorded),
                    reminder.intervalMonths?.toString() ?: stringResource(R.string.maintenance_reminder_not_recorded),
                ),
            )
            if (reminder.usesConfiguredMileageBaseline) {
                Text(
                    stringResource(
                        R.string.maintenance_reminder_configured_baseline,
                        requireNotNull(reminder.baseline.lastServiceMileage),
                    ),
                )
            } else {
                Text(stringResource(R.string.maintenance_reminder_baseline,
                    reminder.baseline.lastServiceMileage?.toString() ?: stringResource(R.string.maintenance_reminder_not_recorded),
                    reminder.baseline.lastServiceDate?.toString() ?: stringResource(R.string.maintenance_reminder_not_recorded)))
            }
            reminder.calculation.nextMileage?.let { Text(stringResource(R.string.maintenance_reminder_next_mileage, it)) }
            reminder.calculation.nextDate?.let { Text(stringResource(R.string.maintenance_reminder_next_date, it.toString())) }
            TextButton(onClick = onCreateMaintenance, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.maintenance_reminder_add_record))
            }
        }
    }
}

@Composable
private fun MaintenanceStatusChip(
    status: MaintenanceReminderStatus,
    reminderType: MaintenanceReminderType,
) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                stringResource(
                    when (status) {
                        MaintenanceReminderStatus.UNKNOWN -> R.string.maintenance_reminder_status_unknown
                        MaintenanceReminderStatus.NORMAL -> R.string.maintenance_reminder_status_normal
                        MaintenanceReminderStatus.UPCOMING -> R.string.maintenance_reminder_status_upcoming
                        MaintenanceReminderStatus.DUE -> when (reminderType) {
                            MaintenanceReminderType.REPLACE -> R.string.maintenance_reminder_status_due
                            MaintenanceReminderType.INSPECT -> R.string.maintenance_reminder_status_inspect_due
                            MaintenanceReminderType.ESTIMATE -> R.string.maintenance_reminder_status_estimate_due
                        }
                        MaintenanceReminderStatus.OVERDUE -> when (reminderType) {
                            MaintenanceReminderType.REPLACE -> R.string.maintenance_reminder_status_overdue
                            MaintenanceReminderType.INSPECT -> R.string.maintenance_reminder_status_inspect_overdue
                            MaintenanceReminderType.ESTIMATE -> R.string.maintenance_reminder_status_estimate_overdue
                        }
                    },
                ),
            )
        },
    )
}

@Composable
private fun NotificationSettingsCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.reminders_notifications_title)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (enabled) R.string.reminders_notifications_enabled else R.string.reminders_notifications_disabled,
                    ),
                )
            },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("reminders-notifications-switch"),
                )
            },
        )
    }
}

@Composable
private fun ReminderCard(
    row: ReminderRow,
    onEvent: (ReminderEvent) -> Unit,
    onRequestCustomSnooze: (Long) -> Unit,
    highlighted: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val reminder = row.reminder
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder-${reminder.id}")
            .semantics { selected = highlighted },
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    reminder.title,
                    fontWeight = if (reminder.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.vehicleName)
                    Text(
                        stringResource(reminder.source.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ReminderDueText(row)
                    if (reminder.source == ReminderSource.LICENSE_TAX ||
                        reminder.source == ReminderSource.ROAD_MAINTENANCE_FEE ||
                        reminder.source == ReminderSource.PERIODIC_INSPECTION
                    ) {
                        Text(
                            stringResource(R.string.reminders_statutory_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!reminder.isEnabled) {
                        Text(stringResource(R.string.reminders_disabled), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!reminder.isCompleted && row.status == ReminderStatus.NORMAL && reminder.dueOdometerKm != null) {
                        Text(
                            reminder.estimatedNotificationEpochDay?.let { estimatedDate ->
                                stringResource(
                                    R.string.reminders_estimated_notification_date,
                                    LocalDate.ofEpochDay(estimatedDate).toString(),
                                )
                            } ?: stringResource(R.string.reminders_mileage_forecast_insufficient),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    reminder.snoozedUntilEpochDay?.let { date ->
                        Text(stringResource(R.string.reminders_snoozed_until, LocalDate.ofEpochDay(date).toString()))
                    }
                }
            },
            trailingContent = {
                Column {
                    ReminderStatusChip(row)
                    if (!reminder.isCompleted) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.reminders_more_actions))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (reminder.source != ReminderSource.SERVICE_ITEM) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reminders_mark_complete)) },
                                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEvent(ReminderEvent.Complete(reminder.id))
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (reminder.isEnabled) R.string.reminders_disable else R.string.reminders_enable,
                                        ),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEvent(ReminderEvent.SetReminderEnabled(reminder.id, !reminder.isEnabled))
                                },
                            )
                            if (reminder.isEnabled) {
                                listOf(1L, 3L, 7L).forEach { days ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.reminders_snooze_days, days)) },
                                        onClick = {
                                            menuExpanded = false
                                            onEvent(ReminderEvent.Snooze(reminder.id, days))
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reminders_snooze_custom)) },
                                    onClick = {
                                        menuExpanded = false
                                        onRequestCustomSnooze(reminder.id)
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ReminderDueText(row: ReminderRow) {
    val dueParts = buildList {
        row.reminder.dueOdometerKm?.let { due ->
            add(stringResource(R.string.reminders_due_odometer, due, row.currentOdometerKm))
        }
        row.reminder.referenceDateEpochDay?.let { referenceDate ->
            add(
                stringResource(
                    R.string.reminders_inspection_reference_date,
                    LocalDate.ofEpochDay(referenceDate).toString(),
                ),
            )
        }
        row.reminder.dueDateEpochDay?.let { due ->
            add(
                stringResource(
                    if (row.reminder.referenceDateEpochDay == null) {
                        R.string.reminders_due_date
                    } else {
                        R.string.reminders_inspection_deadline
                    },
                    LocalDate.ofEpochDay(due).toString(),
                ),
            )
        }
    }
    Text(dueParts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ReminderStatusChip(row: ReminderRow) {
    val reminder = row.reminder
    val (label, color) = when {
        reminder.isCompleted -> stringResource(R.string.reminders_completed) to MaterialTheme.colorScheme.secondary
        !reminder.isEnabled -> stringResource(R.string.reminders_disabled) to MaterialTheme.colorScheme.outline
        row.status == ReminderStatus.OVERDUE -> stringResource(R.string.reminders_status_overdue) to MaterialTheme.colorScheme.error
        row.status == ReminderStatus.DUE_SOON -> stringResource(R.string.reminders_status_due_soon) to MaterialTheme.colorScheme.tertiary
        else -> stringResource(R.string.reminders_status_normal) to MaterialTheme.colorScheme.primary
    }
    AssistChip(
        onClick = {},
        label = { Text(label, color = color) },
        border = null,
    )
}

private fun ReminderSource.labelRes(): Int = when (this) {
    ReminderSource.MANUAL -> R.string.reminders_source_manual
    ReminderSource.SERVICE_ITEM -> R.string.reminders_source_service
    ReminderSource.LICENSE_TAX -> R.string.reminders_source_license_tax
    ReminderSource.ROAD_MAINTENANCE_FEE -> R.string.reminders_source_road_maintenance_fee
    ReminderSource.PERIODIC_INSPECTION -> R.string.reminders_source_periodic_inspection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddManualReminderSheet(
    vehicles: List<ReminderVehicleOption>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String) -> Unit,
) {
    var selectedVehicleId by rememberSaveable { mutableLongStateOf(vehicles.first().id) }
    var title by rememberSaveable { mutableStateOf("") }
    var dueOdometer by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.reminders_add), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.reminders_vehicle), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vehicles.forEach { vehicle ->
                    OutlinedButton(onClick = { selectedVehicleId = vehicle.id }) {
                        Text(
                            if (selectedVehicleId == vehicle.id) {
                                stringResource(R.string.reminders_vehicle_selected, vehicle.name)
                            } else {
                                vehicle.name
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.reminders_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = dueOdometer,
                onValueChange = { dueOdometer = it },
                label = { Text(stringResource(R.string.reminders_due_odometer_input)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                label = { Text(stringResource(R.string.reminders_due_date_input)) },
                placeholder = { Text(stringResource(R.string.local_date_format_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                stringResource(R.string.reminders_due_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onSave(selectedVehicleId, title, dueOdometer, dueDate) },
                modifier = Modifier.align(androidx.compose.ui.Alignment.End),
            ) { Text(stringResource(R.string.save_changes)) }
        }
    }
}

@Composable
private fun SnoozeUntilDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_snooze_custom)) },
        text = {
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text(stringResource(R.string.reminders_due_date_input)) },
                placeholder = { Text(stringResource(R.string.local_date_format_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dateText) }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReminderMessage(stringRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(stringRes),
        modifier = modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}
