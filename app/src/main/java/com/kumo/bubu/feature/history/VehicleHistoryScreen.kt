package com.kumo.bubu.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.ConfirmDeleteDialog
import com.kumo.bubu.domain.model.toFuelEconomyDisplayText
import com.kumo.bubu.feature.fuel.labelRes
import com.kumo.bubu.feature.fuel.toLiterText
import java.time.LocalDate

@Composable
fun VehicleHistoryRoute(
    viewModel: VehicleHistoryViewModel,
    onBack: () -> Unit,
    onEditFuel: (Long) -> Unit,
    onEditMaintenance: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VehicleHistoryScreen(state, viewModel::onEvent, onBack, onEditFuel, onEditMaintenance)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleHistoryScreen(
    state: VehicleHistoryUiState,
    onEvent: (VehicleHistoryEvent) -> Unit,
    onBack: () -> Unit,
    onEditFuel: (Long) -> Unit,
    onEditMaintenance: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> HistoryStatus(padding, loading = true)
            state.loadFailed || state.vehicle == null -> HistoryStatus(padding, loading = false)
            else -> VehicleHistoryContent(state, onEvent, onEditFuel, onEditMaintenance, padding)
        }
    }
    DeleteConfirmation(state, onEvent)
    DeleteError(state, onEvent)
}

@Composable
private fun VehicleHistoryContent(
    state: VehicleHistoryUiState,
    onEvent: (VehicleHistoryEvent) -> Unit,
    onEditFuel: (Long) -> Unit,
    onEditMaintenance: (Long) -> Unit,
    padding: PaddingValues,
) {
    val vehicle = requireNotNull(state.vehicle)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vehicle.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        vehicle.licensePlate ?: stringResource(R.string.dashboard_license_plate_unknown),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.current_odometer_value, vehicle.currentOdometerKm))
                }
            }
        }
        item { HistoryFilters(state.filter, onEvent) }
        item { HistoryDateRange(state, onEvent) }
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(VehicleHistoryEvent.SearchChanged(it)) },
                label = { Text(stringResource(R.string.vehicle_history_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.records.isEmpty()) {
            item { Text(stringResource(R.string.vehicle_history_empty), modifier = Modifier.padding(vertical = 32.dp)) }
        } else {
            items(
                state.records,
                key = { item -> "${if (item is VehicleHistoryItem.Fuel) "fuel" else "maintenance"}-${item.id}" },
            ) { item ->
                HistoryRecordCard(
                    item = item,
                    isDeleting = state.deleteInProgress && state.deleteConfirmation == item,
                    onClick = {
                        when (item) {
                            is VehicleHistoryItem.Fuel -> onEditFuel(item.record.id)
                            is VehicleHistoryItem.Maintenance -> onEditMaintenance(item.details.record.id)
                        }
                    },
                    onDelete = { onEvent(VehicleHistoryEvent.RequestDelete(item)) },
                )
            }
        }
    }
}

@Composable
private fun HistoryDateRange(state: VehicleHistoryUiState, onEvent: (VehicleHistoryEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.vehicle_history_date_range), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.startDate,
                onValueChange = { onEvent(VehicleHistoryEvent.StartDateChanged(it)) },
                label = { Text(stringResource(R.string.vehicle_history_start_date)) },
                placeholder = { Text(stringResource(R.string.local_date_format_hint)) },
                isError = state.hasInvalidDateRange,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.endDate,
                onValueChange = { onEvent(VehicleHistoryEvent.EndDateChanged(it)) },
                label = { Text(stringResource(R.string.vehicle_history_end_date)) },
                placeholder = { Text(stringResource(R.string.local_date_format_hint)) },
                isError = state.hasInvalidDateRange,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.hasInvalidDateRange) {
            Text(stringResource(R.string.vehicle_history_date_range_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryFilters(filter: HistoryFilter, onEvent: (VehicleHistoryEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onEvent(VehicleHistoryEvent.FilterChanged(option)) },
                label = { Text(stringResource(option.labelRes())) },
            )
        }
    }
}

@Composable
private fun HistoryRecordCard(
    item: VehicleHistoryItem,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(enabled = !isDeleting, onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (item is VehicleHistoryItem.Fuel) R.string.vehicle_history_fuel else R.string.vehicle_history_maintenance),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(LocalDate.ofEpochDay(item.dateEpochDay).toString())
                }
                IconButton(enabled = !isDeleting, onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.vehicle_history_more_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = { menuExpanded = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
            when (item) {
                is VehicleHistoryItem.Fuel -> FuelHistorySummary(item)
                is VehicleHistoryItem.Maintenance -> MaintenanceHistorySummary(item)
            }
        }
    }
}

@Composable
private fun FuelHistorySummary(item: VehicleHistoryItem.Fuel) {
    val record = item.record
    Text(stringResource(R.string.fuel_record_odometer, record.odometerKm))
    record.fuelProduct?.let { Text(stringResource(R.string.vehicle_history_fuel_product, stringResource(it.labelRes()))) }
    Text(stringResource(R.string.vehicle_history_fuel_volume, record.fuelVolumeMl.toLiterText()))
    Text(stringResource(R.string.vehicle_history_total_cost, record.totalCostTwd))
    item.fuelEconomyMilliKmPerLiter?.let { economy ->
        Text(stringResource(R.string.vehicle_history_fuel_economy, economy.toFuelEconomyDisplayText()))
    }
}

@Composable
private fun MaintenanceHistorySummary(item: VehicleHistoryItem.Maintenance) {
    Text(stringResource(R.string.service_record_odometer, item.details.record.odometerKm))
    Text(stringResource(R.string.vehicle_history_primary_item, item.primaryItemName))
    Text(stringResource(R.string.vehicle_history_total_cost, item.details.record.totalCostTwd))
}

@Composable
private fun HistoryStatus(padding: PaddingValues, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(stringResource(if (loading) R.string.vehicle_history_loading else R.string.vehicle_history_load_error))
    }
}

@Composable
private fun DeleteConfirmation(state: VehicleHistoryUiState, onEvent: (VehicleHistoryEvent) -> Unit) {
    val item = state.deleteConfirmation ?: return
    ConfirmDeleteDialog(
        title = stringResource(if (item is VehicleHistoryItem.Fuel) R.string.delete_fuel_title else R.string.delete_service_title),
        message = stringResource(if (item is VehicleHistoryItem.Fuel) R.string.vehicle_history_delete_fuel_message else R.string.delete_service_message),
        confirmLabel = stringResource(R.string.delete),
        cancelLabel = stringResource(R.string.cancel),
        onConfirm = { onEvent(VehicleHistoryEvent.ConfirmDelete) },
        onDismiss = { onEvent(VehicleHistoryEvent.DismissDelete) },
    )
}

@Composable
private fun DeleteError(state: VehicleHistoryUiState, onEvent: (VehicleHistoryEvent) -> Unit) {
    if (!state.deleteFailed) return
    AlertDialog(
        onDismissRequest = { onEvent(VehicleHistoryEvent.DismissDeleteError) },
        title = { Text(stringResource(R.string.vehicle_history_delete_error_title)) },
        text = { Text(stringResource(R.string.vehicle_history_delete_error)) },
        confirmButton = {
            TextButton(onClick = { onEvent(VehicleHistoryEvent.DismissDeleteError) }) {
                Text(stringResource(R.string.confirm))
            }
        },
    )
}

private fun HistoryFilter.labelRes(): Int = when (this) {
    HistoryFilter.ALL -> R.string.vehicle_history_filter_all
    HistoryFilter.FUEL -> R.string.vehicle_history_filter_fuel
    HistoryFilter.MAINTENANCE -> R.string.vehicle_history_filter_maintenance
}
