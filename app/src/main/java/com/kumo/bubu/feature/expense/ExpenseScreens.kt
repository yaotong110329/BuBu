package com.kumo.bubu.feature.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.ExpenseRecord

@Composable
fun ExpenseFormRoute(viewModel: ExpenseFormViewModel, onSaved: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.savedEffects.collect { onSaved() } }
    ExpenseFormScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(state: ExpenseFormUiState, onEvent: (ExpenseFormEvent) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.expense_form_title)) }, navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ExpenseVehicleSelector(state, onEvent)
            ExpenseCategorySelector(state, onEvent)
            ExpenseFields(state, onEvent)
        }
    }
}

@Composable private fun ExpenseVehicleSelector(state: ExpenseFormUiState, onEvent: (ExpenseFormEvent) -> Unit) { Text(stringResource(R.string.service_vehicle)); Row { state.vehicles.forEach { vehicle -> FilterChip(vehicle.id == state.vehicleId, { onEvent(ExpenseFormEvent.Vehicle(vehicle.id)) }, { Text(vehicle.name) }) } } }
@Composable private fun ExpenseCategorySelector(state: ExpenseFormUiState, onEvent: (ExpenseFormEvent) -> Unit) { Row { ExpenseCategory.entries.forEach { category -> FilterChip(state.category == category, { onEvent(ExpenseFormEvent.Category(category)) }, { Text(stringResource(category.label())) }) } } }
@Composable private fun ExpenseFields(state: ExpenseFormUiState, onEvent: (ExpenseFormEvent) -> Unit) { OutlinedTextField(state.date, { onEvent(ExpenseFormEvent.Date(it)) }, label = { Text(stringResource(R.string.service_date)) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(state.time, { onEvent(ExpenseFormEvent.Time(it)) }, label = { Text(stringResource(R.string.service_time)) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(state.total, { onEvent(ExpenseFormEvent.Total(it)) }, label = { Text(stringResource(R.string.expense_total)) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(state.note, { onEvent(ExpenseFormEvent.Note(it)) }, label = { Text(stringResource(R.string.service_note)) }, modifier = Modifier.fillMaxWidth()); if (state.editId == null && (state.category == ExpenseCategory.LICENSE_TAX || state.category == ExpenseCategory.ROAD_MAINTENANCE_FEE)) { Row { Checkbox(checked = state.completeSameCycleReminder, onCheckedChange = { onEvent(ExpenseFormEvent.CompleteSameCycleReminder(it)) }); Text(stringResource(R.string.expense_complete_same_cycle_reminder), modifier = Modifier.padding(top = 12.dp)) } }; if (state.error) Text(stringResource(R.string.expense_input_error)); Button({ onEvent(ExpenseFormEvent.Save) }, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving && !state.isLoading) { Text(stringResource(R.string.save_expense)) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseRecordsRoute(viewModel: ExpenseRecordsViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.expense_records_title)) }) }) { padding -> ExpenseRecordList(state, padding, onAdd, onEdit) { pendingDeleteId = it } }
    pendingDeleteId?.let { id -> ExpenseDeleteDialog({ viewModel.delete(id); pendingDeleteId = null }, { pendingDeleteId = null }) }
}

@Composable private fun ExpenseRecordList(state: ExpenseRecordsUiState, padding: androidx.compose.foundation.layout.PaddingValues, onAdd: () -> Unit, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(padding)) { item { Button(onClick = onAdd, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.add_expense)) } }; if (state.deleteFailed) item { Text(stringResource(R.string.expense_delete_error), modifier = Modifier.padding(16.dp)) }; items(state.records, key = { it.id }) { record -> ExpenseRecordCard(record, onEdit, onDelete) } }
@Composable private fun ExpenseRecordCard(record: ExpenseRecord, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) = Card(Modifier.fillMaxWidth().padding(8.dp)) { Column(Modifier.padding(16.dp)) { Text(stringResource(record.category.label())); Text(stringResource(R.string.service_record_total, record.totalCostTwd)); Row { TextButton({ onEdit(record.id) }) { Text(stringResource(R.string.edit)) }; TextButton({ onDelete(record.id) }) { Text(stringResource(R.string.delete)) } } } }
@Composable private fun ExpenseDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.delete_expense_title)) }, text = { Text(stringResource(R.string.delete_expense_message)) }, confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
private fun ExpenseCategory.label() = when (this) { ExpenseCategory.LICENSE_TAX -> R.string.expense_license_tax; ExpenseCategory.ROAD_MAINTENANCE_FEE -> R.string.expense_road_fee; ExpenseCategory.INSURANCE -> R.string.expense_insurance; ExpenseCategory.PARKING -> R.string.expense_parking; ExpenseCategory.TOLL -> R.string.expense_toll; ExpenseCategory.FINE -> R.string.expense_fine; ExpenseCategory.CAR_CARE -> R.string.expense_car_care; ExpenseCategory.OTHER -> R.string.expense_other }
