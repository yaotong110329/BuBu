package com.kumo.bubu.feature.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ServiceRecordType
import java.time.LocalDate

@Composable
fun ServiceRecordsRoute(viewModel: ServiceRecordsViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServiceRecordsScreen(state, viewModel::onEvent, onAdd, onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceRecordsScreen(state: ServiceRecordsUiState, onEvent: (ServiceRecordsEvent) -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.service_records_title)) }) }, floatingActionButton = { if (state.currentVehicleId != null) ExtendedFloatingActionButton(onClick = onAdd, text = { Text(stringResource(R.string.add_service_title)) }, icon = { Text(stringResource(R.string.add_symbol)) }) }) { padding ->
        when { state.isLoading -> Status(padding, R.string.loading_service_records); state.failed -> Status(padding, R.string.service_load_error); state.records.isEmpty() -> Status(padding, R.string.no_service_records); else -> ServiceRecordList(state.records, padding, onEdit) { pendingDeleteId = it } }
    }
    if (state.deleteFailed) Text(stringResource(R.string.service_delete_error))
    pendingDeleteId?.let { recordId -> DeleteDialog(R.string.delete_service_title, R.string.delete_service_message, { onEvent(ServiceRecordsEvent.Delete(recordId)); pendingDeleteId = null }, { pendingDeleteId = null }) }
}

@Composable private fun ServiceRecordList(records: List<ServiceRecordRow>, padding: PaddingValues, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(records, key = { it.record.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(row.record.title); Text(row.vehicleName); Text(LocalDate.ofEpochDay(row.record.dateEpochDay).toString()); Text(stringResource(row.record.recordType.labelRes())); Text(stringResource(R.string.service_record_total, row.record.totalCostTwd)); Text(stringResource(R.string.service_record_odometer, row.record.odometerKm)); Row { TextButton({ onEdit(row.record.id) }) { Text(stringResource(R.string.edit)) }; TextButton({ onDelete(row.record.id) }) { Text(stringResource(R.string.delete)) } } } } } }
@Composable private fun DeleteDialog(title: Int, message: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { Text(stringResource(message)) }, confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
@Composable private fun Status(padding: PaddingValues, text: Int) { Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text(stringResource(text)) } }
