package com.kumo.bubu.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.bringIntoViewOnFocus
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.VehicleType
import java.time.LocalDate

@Composable
fun VehicleFormRoute(
    viewModel: VehicleFormViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect == VehicleFormEffect.Saved) onSaved()
        }
    }
    VehicleFormScreen(uiState, viewModel::onEvent, onBack)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VehicleFormScreen(
    uiState: VehicleFormUiState,
    onEvent: (VehicleFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditing) R.string.edit_vehicle_title else R.string.add_vehicle_title,
                        ),
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> CenteredMessage(modifier = Modifier.padding(padding), loading = true)
            uiState.loadFailed -> CenteredMessage(modifier = Modifier.padding(padding), loading = false)
            else -> VehicleFormContent(uiState, onEvent, Modifier.padding(padding))
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, loading: Boolean) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(
            stringResource(if (loading) R.string.loading_vehicle else R.string.vehicle_load_error),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun VehicleFormContent(
    state: VehicleFormUiState,
    onEvent: (VehicleFormEvent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FormTextField(state.name, { onEvent(VehicleFormEvent.NameChanged(it)) }, R.string.vehicle_name, state.error(VehicleFormField.NAME))
        VehicleTypeSection(state, onEvent)
        FormTextField(state.brand, { onEvent(VehicleFormEvent.BrandChanged(it)) }, R.string.vehicle_brand)
        FormTextField(state.model, { onEvent(VehicleFormEvent.ModelChanged(it)) }, R.string.vehicle_model)
        FormTextField(
            state.manufactureYear,
            { onEvent(VehicleFormEvent.ManufactureYearChanged(it)) },
            R.string.manufacture_year,
            state.error(VehicleFormField.MANUFACTURE_YEAR),
            KeyboardType.Number,
        )
        FormTextField(
            state.engineDisplacementCc,
            { onEvent(VehicleFormEvent.EngineDisplacementChanged(it)) },
            R.string.engine_displacement,
            state.error(VehicleFormField.ENGINE_DISPLACEMENT),
            KeyboardType.Number,
            R.string.cc_unit,
        )
        FormTextField(state.licensePlate, { onEvent(VehicleFormEvent.LicensePlateChanged(it)) }, R.string.license_plate)
        PowertrainSection(state.powertrainType) { onEvent(VehicleFormEvent.PowertrainTypeChanged(it)) }
        InspectionScheduleSection(state, onEvent)
        FormTextField(
            state.trackingStartDate,
            { onEvent(VehicleFormEvent.TrackingStartDateChanged(it)) },
            R.string.tracking_start_date,
            state.error(VehicleFormField.TRACKING_START_DATE),
            placeholderRes = R.string.local_date_format_hint,
        )
        FormTextField(
            state.trackingStartOdometerKm,
            { onEvent(VehicleFormEvent.TrackingStartOdometerChanged(it)) },
            R.string.tracking_start_odometer,
            state.error(VehicleFormField.TRACKING_START_ODOMETER),
            KeyboardType.Number,
            R.string.kilometer_unit,
        )
        FormTextField(state.note, { onEvent(VehicleFormEvent.NoteChanged(it)) }, R.string.vehicle_note, singleLine = false)
        SaveSection(state, onEvent)
    }
}

@Composable
private fun InspectionScheduleSection(state: VehicleFormUiState, onEvent: (VehicleFormEvent) -> Unit) {
    val eligibleType = state.vehicleType == VehicleType.CAR ||
        (state.vehicleType == VehicleType.MOTORCYCLE && state.motorcycleClass == MotorcycleClass.LARGE_HEAVY)
    val manufactureYear = state.manufactureYear.toIntOrNull()
    val vehicleAge = manufactureYear?.let { LocalDate.now().year - it }
    if (!eligibleType || vehicleAge == null || vehicleAge < 5) return

    Text(stringResource(R.string.vehicle_inspection_schedule), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.vehicle_inspection_schedule_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FormTextField(
        state.primaryInspectionMonthDay,
        { onEvent(VehicleFormEvent.PrimaryInspectionMonthDayChanged(it)) },
        R.string.vehicle_primary_inspection_month_day,
        state.error(VehicleFormField.PRIMARY_INSPECTION_MONTH_DAY),
        placeholderRes = R.string.month_day_format_hint,
    )
    if (vehicleAge >= 10) {
        FormTextField(
            state.secondaryInspectionMonthDay,
            { onEvent(VehicleFormEvent.SecondaryInspectionMonthDayChanged(it)) },
            R.string.vehicle_secondary_inspection_month_day,
            state.error(VehicleFormField.SECONDARY_INSPECTION_MONTH_DAY),
            placeholderRes = R.string.month_day_format_hint,
        )
    }
}

@Composable
private fun VehicleTypeSection(state: VehicleFormUiState, onEvent: (VehicleFormEvent) -> Unit) {
    Text(stringResource(R.string.vehicle_type), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleType.entries.forEach { type ->
            FilterChip(
                selected = state.vehicleType == type,
                onClick = { onEvent(VehicleFormEvent.VehicleTypeChanged(type)) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
    if (state.vehicleType == VehicleType.MOTORCYCLE) MotorcycleClassSection(state, onEvent)
}

@Composable
private fun MotorcycleClassSection(state: VehicleFormUiState, onEvent: (VehicleFormEvent) -> Unit) {
    Text(
        stringResource(R.string.motorcycle_class),
        style = MaterialTheme.typography.titleSmall,
        color = if (state.error(VehicleFormField.MOTORCYCLE_CLASS) != null) {
            MaterialTheme.colorScheme.error
        } else MaterialTheme.colorScheme.onSurface,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MotorcycleClass.entries.forEach { motorcycleClass ->
            FilterChip(
                selected = state.motorcycleClass == motorcycleClass,
                onClick = { onEvent(VehicleFormEvent.MotorcycleClassChanged(motorcycleClass)) },
                label = { Text(stringResource(motorcycleClass.labelRes())) },
            )
        }
    }
    state.error(VehicleFormField.MOTORCYCLE_CLASS)?.let { FormError(it) }
}

@Composable
private fun PowertrainSection(selected: PowertrainType?, onSelected: (PowertrainType?) -> Unit) {
    Text(stringResource(R.string.powertrain_type), style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.powertrain_unspecified)) },
        )
        PowertrainType.entries.chunked(2).forEach { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTypes.forEach { type ->
                    FilterChip(
                        selected = selected == type,
                        onClick = { onSelected(type) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    error: VehicleFormError? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffixRes: Int? = null,
    placeholderRes: Int? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        label = { Text(stringResource(labelRes)) },
        suffix = suffixRes?.let { { Text(stringResource(it)) } },
        placeholder = placeholderRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = error?.let { { FormError(it) } },
    )
}

@Composable
private fun SaveSection(state: VehicleFormUiState, onEvent: (VehicleFormEvent) -> Unit) {
    if (state.saveFailed) Text(stringResource(R.string.vehicle_save_error), color = MaterialTheme.colorScheme.error)
    Button(
        onClick = { onEvent(VehicleFormEvent.Save) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isSaving,
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
        }
        Text(stringResource(R.string.save_vehicle))
    }
}

@Composable
private fun FormError(error: VehicleFormError) {
    Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
}

private fun VehicleFormUiState.error(field: VehicleFormField) = errors[field]

private fun VehicleFormError.messageRes(): Int = when (this) {
    VehicleFormError.REQUIRED -> R.string.required_field_error
    VehicleFormError.INVALID_DATE -> R.string.invalid_date_error
    VehicleFormError.FUTURE_DATE -> R.string.future_date_error
    VehicleFormError.INVALID_YEAR -> R.string.invalid_year_error
    VehicleFormError.POSITIVE_INTEGER_REQUIRED -> R.string.positive_integer_error
    VehicleFormError.NON_NEGATIVE_INTEGER_REQUIRED -> R.string.non_negative_integer_error
    VehicleFormError.INVALID_MONTH_DAY -> R.string.invalid_month_day_error
}
