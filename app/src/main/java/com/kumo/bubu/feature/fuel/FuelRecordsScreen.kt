package com.kumo.bubu.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.ConfirmDeleteDialog
import java.time.LocalDate

@Composable
fun FuelRecordsRoute(
    viewModel: FuelRecordsViewModel,
    onAddFuel: () -> Unit,
    onEditFuel: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FuelRecordsScreen(state, viewModel::onEvent, onAddFuel, onEditFuel)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FuelRecordsScreen(
    state: FuelRecordsUiState,
    onEvent: (FuelRecordsEvent) -> Unit,
    onAddFuel: () -> Unit,
    onEditFuel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
        floatingActionButton = {
            if (state.currentVehicleId != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddFuel,
                    text = { Text(stringResource(R.string.add_fuel)) },
                    icon = { Text(stringResource(R.string.add_symbol)) },
                )
            }
        },
    ) { padding -> FuelRecordBody(state, onEvent, onEditFuel, padding) }
    FuelRecordDeleteConfirmation(state, onEvent)
    FuelRecordDeleteError(state, onEvent)
}

@Composable
private fun FuelRecordBody(
    state: FuelRecordsUiState,
    onEvent: (FuelRecordsEvent) -> Unit,
    onEditFuel: (Long) -> Unit,
    padding: PaddingValues,
) {
    when {
        state.isLoading -> FuelRecordStatus(padding, R.string.loading_fuel_records, true)
        state.loadFailed -> FuelRecordStatus(padding, R.string.fuel_load_error, false)
        state.records.isEmpty() -> FuelRecordStatus(padding, R.string.no_fuel_records, false)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.records, key = { it.record.id }) { row ->
                FuelRecordCard(
                    row = row,
                    isBusy = row.record.id == state.actionInProgressRecordId,
                    onEdit = { onEditFuel(row.record.id) },
                    onDelete = { onEvent(FuelRecordsEvent.RequestDelete(row.record.id)) },
                )
            }
        }
    }
}

@Composable
private fun FuelRecordStatus(padding: PaddingValues, messageRes: Int, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(stringResource(messageRes), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun FuelRecordCard(
    row: FuelRecordRow,
    isBusy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val record = row.record
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.vehicleName, style = MaterialTheme.typography.titleMedium)
            Text(LocalDate.ofEpochDay(record.dateEpochDay).toString())
            Text(stringResource(R.string.fuel_record_volume_cost, record.fuelVolumeMl.toLiterText(), record.totalCostTwd))
            Text(stringResource(R.string.fuel_record_odometer, record.odometerKm))
            if (record.isFullTank) Text(stringResource(R.string.fuel_full_tank), color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !isBusy, onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(enabled = !isBusy, onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun FuelRecordDeleteConfirmation(state: FuelRecordsUiState, onEvent: (FuelRecordsEvent) -> Unit) {
    val row = state.records.firstOrNull { it.record.id == state.deleteConfirmationRecordId } ?: return
    ConfirmDeleteDialog(
        title = stringResource(R.string.delete_fuel_title),
        message = stringResource(R.string.delete_fuel_message, row.vehicleName),
        confirmLabel = stringResource(R.string.delete),
        cancelLabel = stringResource(R.string.cancel),
        onConfirm = { onEvent(FuelRecordsEvent.ConfirmDelete) },
        onDismiss = { onEvent(FuelRecordsEvent.DismissDelete) },
    )
}

@Composable
private fun FuelRecordDeleteError(state: FuelRecordsUiState, onEvent: (FuelRecordsEvent) -> Unit) {
    if (!state.deleteFailed) return
    AlertDialog(
        onDismissRequest = { onEvent(FuelRecordsEvent.DismissError) },
        title = { Text(stringResource(R.string.fuel_delete_error_title)) },
        text = { Text(stringResource(R.string.fuel_delete_error)) },
        confirmButton = {
            TextButton(onClick = { onEvent(FuelRecordsEvent.DismissError) }) {
                Text(stringResource(R.string.confirm))
            }
        },
    )
}

internal fun Long.toLiterText(): String {
    val whole = this / 1_000
    val fraction = (this % 1_000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
