package com.kumo.bubu.feature.fuel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.bringIntoViewOnFocus
import com.kumo.bubu.core.ui.components.LocalDatePickerDialog
import com.kumo.bubu.core.ui.components.LocalDateTimeField
import com.kumo.bubu.core.ui.components.LocalTimePickerDialog
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun FuelFormRoute(
    viewModel: FuelFormViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect -> if (effect is FuelFormEffect.Saved) onSaved() }
    }
    FuelFormScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelFormScreen(
    state: FuelFormUiState,
    onEvent: (FuelFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = FuelRecordScreen(
    state = state,
    onEvent = onEvent,
    onBack = onBack,
    modifier = modifier,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelRecordScreen(
    state: FuelFormUiState,
    onEvent: (FuelFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.fuel_record_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(stringResource(R.string.fuel_back_symbol))
                    }
                },
                actions = {
                    VehicleTitleMenu(state = state, onEvent = onEvent)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            SaveButtonBar(
                isSaving = state.isSaving,
                canSave = state.canSave,
                onSave = { onEvent(FuelFormEvent.Save) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BasicInfoCard(
                    state = state,
                    onEvent = onEvent,
                    onDateTimeClick = { showDatePicker = true },
                )
            }
            item { FuelInputCard(state = state, onEvent = onEvent) }
            item {
                AdvancedSettingsSection(
                    expanded = showAdvancedSettings,
                    onExpandedChange = { showAdvancedSettings = it },
                    state = state,
                    onEvent = onEvent,
                )
            }
            if (state.saveFailed) {
                item {
                    Text(
                        text = stringResource(R.string.fuel_save_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            date = state.date,
            onDateSelected = {
                onEvent(FuelFormEvent.DateChanged(it))
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
                onEvent(FuelFormEvent.TimeChanged(it))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
    OdometerOrderWarningDialog(state, onEvent)
}

@Composable
private fun VehicleTitleMenu(
    state: FuelFormUiState,
    onEvent: (FuelFormEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = state.activeVehicles.isNotEmpty() && !state.isSaving,
        ) {
            Text(
                text = state.selectedVehicle?.name ?: stringResource(R.string.fuel_vehicle),
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.activeVehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicle.name) },
                    onClick = {
                        expanded = false
                        onEvent(FuelFormEvent.VehicleChanged(vehicle.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun BasicInfoCard(
    state: FuelFormUiState,
    onEvent: (FuelFormEvent) -> Unit,
    onDateTimeClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.fuel_basic_info),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LocalDateTimeField(
                    date = state.date,
                    time = state.time,
                    enabled = !state.isSaving,
                    isError = state.error(FuelFormField.DATE) != null || state.error(FuelFormField.TIME) != null,
                    onClick = onDateTimeClick,
                    modifier = Modifier.weight(3f),
                    supportingContent = {
                        state.error(FuelFormField.DATE)?.let { FuelFieldError(it) }
                        state.error(FuelFormField.TIME)?.let { FuelFieldError(it) }
                    },
                )
                Column(modifier = Modifier.weight(2f)) {
                    FuelTextField(
                        value = state.odometerKm,
                        onValueChange = { onEvent(FuelFormEvent.OdometerChanged(it)) },
                        labelRes = R.string.fuel_odometer,
                        error = state.error(FuelFormField.ODOMETER),
                        keyboardType = KeyboardType.Number,
                        enabled = !state.isSaving,
                    )
                    state.selectedVehicle?.let { vehicle ->
                        Text(
                            text = stringResource(
                                R.string.fuel_current_odometer_hint,
                                vehicle.currentOdometerKm,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelInputCard(
    state: FuelFormUiState,
    onEvent: (FuelFormEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.fuel_info),
                style = MaterialTheme.typography.titleMedium,
            )
            FuelTextField(
                value = state.totalCostTwd,
                onValueChange = { onEvent(FuelFormEvent.TotalCostChanged(it)) },
                labelRes = R.string.fuel_total_cost,
                error = state.error(FuelFormField.TOTAL_COST),
                keyboardType = KeyboardType.Number,
                calculated = state.calculatedField == FuelCalculationField.TOTAL_COST,
                enabled = !state.isSaving,
                emphasized = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FuelTextField(
                    value = state.volumeLiters,
                    onValueChange = { onEvent(FuelFormEvent.VolumeChanged(it)) },
                    labelRes = R.string.fuel_volume,
                    error = state.error(FuelFormField.VOLUME),
                    keyboardType = KeyboardType.Decimal,
                    calculated = state.calculatedField == FuelCalculationField.VOLUME,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                )
                FuelTextField(
                    value = state.pricePerLiter,
                    onValueChange = { onEvent(FuelFormEvent.PriceChanged(it)) },
                    labelRes = R.string.fuel_price_per_liter,
                    error = state.error(FuelFormField.PRICE),
                    keyboardType = KeyboardType.Decimal,
                    calculated = state.calculatedField == FuelCalculationField.PRICE,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
            FuelPriceSummary(state)
            if (state.hasHighVolumeWarning) {
                Text(
                    text = stringResource(R.string.fuel_high_volume_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FuelPriceSummary(state: FuelFormUiState) {
    state.fuelProduct?.let { product ->
        Text(
            text = stringResource(
                R.string.fuel_selected_product_price,
                stringResource(product.labelRes()),
                state.pricePerLiter.ifBlank { stringResource(R.string.fuel_price_unavailable_value) },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val sourceText = when (state.priceSource) {
        FuelPriceSource.NONE, FuelPriceSource.MANUAL, FuelPriceSource.CALCULATED -> null
        FuelPriceSource.LOADING -> stringResource(R.string.fuel_price_loading)
        FuelPriceSource.CPC_MANUAL -> state.priceEffectiveDateEpochDay?.let {
            stringResource(R.string.fuel_price_cpc_manual, LocalDate.ofEpochDay(it).toString())
        }
        FuelPriceSource.LAST_RECORD -> stringResource(R.string.fuel_price_last_record)
        FuelPriceSource.CPC_FETCH_FAILED_LAST_RECORD -> stringResource(R.string.fuel_price_cpc_failed_last_record)
        FuelPriceSource.CPC_FETCH_FAILED -> stringResource(R.string.fuel_price_cpc_failed)
        FuelPriceSource.UNAVAILABLE -> stringResource(R.string.fuel_price_unavailable)
    }
    sourceText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
internal fun AdvancedSettingsSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: FuelFormUiState,
    onEvent: (FuelFormEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            TextButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.fuel_advanced_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            if (expanded) R.string.fuel_advanced_settings_collapse
                            else R.string.fuel_advanced_settings_expand,
                        ),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FuelingModeChips(
                        selected = state.fuelingMode,
                        enabled = !state.isSaving,
                        onSelected = { onEvent(FuelFormEvent.FuelingModeChanged(it)) },
                    )
                    FuelProductChips(
                        selected = state.fuelProduct,
                        enabled = !state.isSaving,
                        onSelected = { onEvent(FuelFormEvent.FuelProductChanged(it)) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.fuel_full_tank),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.fuel_full_tank_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.isFullTank,
                            onCheckedChange = { onEvent(FuelFormEvent.FullTankChanged(it)) },
                            enabled = !state.isSaving,
                        )
                    }
                    if (!state.isEditing && state.fuelProduct != null) {
                        TextButton(
                            onClick = { onEvent(FuelFormEvent.RefreshPrice) },
                            enabled = state.priceSource != FuelPriceSource.LOADING,
                        ) {
                            if (state.priceSource == FuelPriceSource.LOADING) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.fuel_price_refresh))
                            }
                        }
                    }
                    FuelTextField(
                        value = state.note,
                        onValueChange = { onEvent(FuelFormEvent.NoteChanged(it)) },
                        labelRes = R.string.fuel_note,
                        enabled = !state.isSaving,
                        minLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelingModeChips(
    selected: FuelingMode,
    enabled: Boolean,
    onSelected: (FuelingMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.fuel_fueling_mode), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelingMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    enabled = enabled,
                    label = {
                        Text(
                            stringResource(
                                if (mode == FuelingMode.FULL_SERVICE) R.string.fuel_fueling_mode_full_service
                                else R.string.fuel_fueling_mode_self_service,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FuelProductChips(
    selected: FuelProduct?,
    enabled: Boolean,
    onSelected: (FuelProduct?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.fuel_product),
            style = MaterialTheme.typography.bodyLarge,
        )
        val products = listOf<FuelProduct?>(null) + FuelProduct.entries
        products.chunked(3).forEach { productRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                productRow.forEach { product ->
                    FilterChip(
                        selected = selected == product,
                        onClick = { onSelected(product) },
                        enabled = enabled,
                        label = {
                            Text(
                                product?.let { stringResource(it.labelRes()) }
                                    ?: stringResource(R.string.powertrain_unspecified),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - productRow.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SaveButtonBar(
    isSaving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.imePadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Button(
            onClick = onSave,
            enabled = canSave,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.fuel_save_record),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun FuelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
    error: FuelFormError? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    calculated: Boolean = false,
    enabled: Boolean = true,
    minLines: Int = 1,
    emphasized: Boolean = false,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(labelRes)) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            enabled = enabled,
            minLines = minLines,
            singleLine = minLines == 1,
            textStyle = if (emphasized) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.bodyLarge
            },
            shape = RoundedCornerShape(if (emphasized) 20.dp else 18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
        if (calculated) {
            Text(
                text = stringResource(R.string.fuel_calculated_value),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let { FuelFieldError(it) }
    }
}

@Composable
private fun FuelFieldError(error: FuelFormError) {
    Text(
        text = stringResource(error.messageRes()),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun OdometerOrderWarningDialog(state: FuelFormUiState, onEvent: (FuelFormEvent) -> Unit) {
    val warning = state.odometerOrderWarning ?: return
    AlertDialog(
        onDismissRequest = { onEvent(FuelFormEvent.DismissOdometerOrder) },
        title = { Text(stringResource(R.string.fuel_odometer_order_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fuel_odometer_order_warning_message))
                warning.previous?.let {
                    Text(
                        stringResource(
                            R.string.fuel_odometer_previous_record,
                            LocalDate.ofEpochDay(it.dateEpochDay).toString(),
                            it.odometerKm,
                        ),
                    )
                }
                warning.next?.let {
                    Text(
                        stringResource(
                            R.string.fuel_odometer_next_record,
                            LocalDate.ofEpochDay(it.dateEpochDay).toString(),
                            it.odometerKm,
                        ),
                    )
                }
                OutlinedTextField(
                    value = state.odometerOrderReason,
                    onValueChange = { onEvent(FuelFormEvent.OdometerOrderReasonChanged(it)) },
                    label = { Text(stringResource(R.string.fuel_odometer_order_reason)) },
                    isError = state.odometerOrderReasonRequired,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.odometerOrderReasonRequired) {
                    Text(
                        text = stringResource(R.string.fuel_odometer_order_reason_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.isSaving,
                onClick = { onEvent(FuelFormEvent.ConfirmOdometerOrder) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(FuelFormEvent.DismissOdometerOrder) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun FuelFormUiState.error(field: FuelFormField): FuelFormError? = errors[field]

private const val MILLIS_PER_DAY = 86_400_000L
