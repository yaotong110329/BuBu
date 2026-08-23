package com.kumo.bubu.feature.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import java.time.LocalDate

@Composable
fun RemindersRoute(
    viewModel: RemindersViewModel,
    onBack: () -> Unit,
    highlightedReminderId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    RemindersScreen(state, viewModel::onEvent, onBack, highlightedReminderId, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onEvent: (ReminderEvent) -> Unit,
    onBack: () -> Unit,
    highlightedReminderId: Long? = null,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var customSnoozeReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
}

@Composable
private fun ReminderContent(
    state: RemindersUiState,
    onEvent: (ReminderEvent) -> Unit,
    onRequestCustomSnooze: (Long) -> Unit,
    highlightedReminderId: Long?,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onEvent(ReminderEvent.SetNotificationsEnabled(granted))
    }
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedReminderId, state.rows) {
        val targetIndex = state.rows.indexOfFirst { it.reminder.id == highlightedReminderId }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex + 1)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NotificationSettingsCard(
                enabled = state.notificationsEnabled,
                onEnabledChange = { enabled ->
                    when {
                        !enabled -> onEvent(ReminderEvent.SetNotificationsEnabled(false))
                        notificationsAllowed -> onEvent(ReminderEvent.SetNotificationsEnabled(true))
                        else -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        when {
            state.isLoading -> item { ReminderMessage(R.string.reminders_loading) }
            state.loadFailed -> item { ReminderMessage(R.string.reminders_load_error) }
            state.vehicles.isEmpty() -> item { ReminderMessage(R.string.reminders_no_vehicles) }
            state.rows.isEmpty() -> item { ReminderMessage(R.string.reminders_empty) }
            else -> {
                if (state.actionFailed) item { ReminderMessage(R.string.reminders_action_error) }
                items(state.rows, key = { it.reminder.id }) { row ->
                    ReminderCard(row, onEvent, onRequestCustomSnooze)
                }
            }
        }
    }
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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val reminder = row.reminder
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder-${reminder.id}"),
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
