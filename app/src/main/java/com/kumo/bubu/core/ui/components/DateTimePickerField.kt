package com.kumo.bubu.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import java.time.LocalDate
import java.time.LocalTime

/**
 * A display-only local date or time field whose overlay owns pointer input.
 *
 * `OutlinedTextField` consumes taps even when it is read-only, so attaching a
 * click handler to the field itself prevents picker dialogs from opening on
 * some devices. The overlay preserves the field's visual treatment while
 * routing every tap to the supplied picker action.
 */
@Composable
fun LocalDateTimePickerField(
    value: String,
    @StringRes labelRes: Int,
    enabled: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
) {
    val fieldDescription = stringResource(labelRes)
    Column(modifier) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                isError = isError,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .semantics {
                        contentDescription = fieldDescription
                        role = Role.Button
                    }
                    .clickable(enabled = enabled, onClick = onClick),
            )
        }
        supportingContent?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    date: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialDate = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.toEpochDay() * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY).toString())
                }
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = pickerState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTimePickerDialog(
    time: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialTime = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.now() }
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_time)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onTimeSelected("%02d:%02d".format(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L
