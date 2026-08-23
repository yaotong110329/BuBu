package com.kumo.bubu.feature.service

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.bringIntoViewOnFocus
import com.kumo.bubu.core.ui.components.LocalDatePickerDialog
import com.kumo.bubu.core.ui.components.LocalDateTimeField
import com.kumo.bubu.core.ui.components.LocalTimePickerDialog
import com.kumo.bubu.domain.model.ServiceRecordType

@Composable
internal fun ServiceWorkOrderCard(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.service_work_order_details),
                style = MaterialTheme.typography.titleMedium,
            )
            ServiceVehicleSelector(state, onEvent)
            ServiceDateTimeFields(state, onEvent)
            ServiceTextField(
                value = state.odometer,
                onValueChange = { onEvent(ServiceFormEvent.OdometerChanged(it)) },
                labelRes = R.string.service_odometer,
                keyboardType = KeyboardType.Number,
                error = state.error.takeIf { it == ServiceFormError.INVALID_ODOMETER },
                enabled = !state.isSaving,
            )
            state.latestOdometerKm?.let { latestOdometer ->
                Text(
                    text = stringResource(
                        R.string.service_current_odometer_hint,
                        latestOdometer,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ServiceRecordTypeSelector(state.recordType, enabled = !state.isSaving) {
                onEvent(ServiceFormEvent.RecordTypeChanged(it))
            }
        }
    }
}

@Composable
private fun ServiceVehicleSelector(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.service_vehicle),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.vehicles.forEach { vehicle ->
            FilterChip(
                selected = vehicle.id == state.vehicleId,
                onClick = { onEvent(ServiceFormEvent.VehicleChanged(vehicle.id)) },
                enabled = !state.isSaving,
                label = { Text(vehicle.name) },
            )
        }
    }
    state.error.takeIf { it == ServiceFormError.VEHICLE_REQUIRED }?.let {
        ServiceFormErrorMessage(it)
    }
}

@Composable
private fun ServiceDateTimeFields(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val dateTimeError = state.error.takeIf {
        it == ServiceFormError.INVALID_DATE || it == ServiceFormError.FUTURE_DATE ||
            it == ServiceFormError.INVALID_TIME
    }
    LocalDateTimeField(
        date = state.date,
        time = state.time,
        enabled = !state.isSaving,
        isError = dateTimeError != null,
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth(),
        supportingContent = { dateTimeError?.let { ServiceFormErrorMessage(it) } },
    )
    if (showDatePicker) {
        LocalDatePickerDialog(
            date = state.date,
            onDateSelected = {
                onEvent(ServiceFormEvent.DateChanged(it))
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        LocalTimePickerDialog(
            time = state.time,
            onTimeSelected = {
                onEvent(ServiceFormEvent.TimeChanged(it))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun ServiceRecordTypeSelector(
    selected: ServiceRecordType,
    enabled: Boolean,
    onSelected: (ServiceRecordType) -> Unit,
) {
    Text(
        text = stringResource(R.string.service_type),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServiceRecordType.entries.forEach { recordType ->
            FilterChip(
                selected = recordType == selected,
                onClick = { onSelected(recordType) },
                enabled = enabled,
                label = { Text(stringResource(recordType.labelRes())) },
            )
        }
    }
}

@Composable
internal fun ServiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    @StringRes placeholderRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    minLines: Int = 1,
    error: ServiceFormError? = null,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(labelRes)) },
            placeholder = placeholderRes?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            readOnly = readOnly,
            enabled = enabled,
            singleLine = minLines == 1,
            minLines = minLines,
            isError = error != null,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
        error?.let { ServiceFormErrorMessage(it) }
    }
}

@Composable
internal fun ServiceFormErrorMessage(error: ServiceFormError) {
    Text(
        text = stringResource(error.messageRes()),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
