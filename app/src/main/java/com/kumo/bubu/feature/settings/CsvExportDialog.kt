package com.kumo.bubu.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R

@Composable
fun CsvExportDialog(
    state: CsvExportUiState,
    onEvent: (CsvExportEvent) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.csv_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CsvExportVehicleSelector(state, onEvent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.startDate,
                        onValueChange = { onEvent(CsvExportEvent.ChangeStartDate(it)) },
                        label = { Text(stringResource(R.string.csv_export_start_date)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.endDate,
                        onValueChange = { onEvent(CsvExportEvent.ChangeEndDate(it)) },
                        label = { Text(stringResource(R.string.csv_export_end_date)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                CsvExportErrorText(state.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isExporting) {
                Text(stringResource(R.string.csv_export_choose_destination))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isExporting) {
                Text(stringResource(R.string.csv_export_cancel))
            }
        },
    )
}

@Composable
private fun CsvExportVehicleSelector(
    state: CsvExportUiState,
    onEvent: (CsvExportEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.includeAllVehicles,
            onCheckedChange = { onEvent(CsvExportEvent.SetIncludeAllVehicles(it)) },
        )
        Text(stringResource(R.string.csv_export_all_vehicles))
    }
    if (!state.includeAllVehicles) {
        Column(modifier = Modifier.fillMaxWidth()) {
            state.vehicles.forEach { vehicle ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = vehicle.id in state.selectedVehicleIds,
                        onCheckedChange = { onEvent(CsvExportEvent.ToggleVehicle(vehicle.id)) },
                    )
                    Text(
                        text = if (vehicle.isArchived) {
                            stringResource(R.string.csv_export_archived_vehicle, vehicle.name)
                        } else {
                            vehicle.name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CsvExportErrorText(error: CsvExportError?) {
    val label = when (error) {
        CsvExportError.INVALID_DATE_RANGE -> R.string.csv_export_invalid_date_range
        CsvExportError.NO_VEHICLE_SELECTED -> R.string.csv_export_no_vehicle_selected
        CsvExportError.WRITE_FAILED -> R.string.csv_export_write_failed
        null -> return
    }
    Text(
        text = stringResource(label),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}
