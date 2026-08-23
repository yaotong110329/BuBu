package com.kumo.bubu.feature.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.VehicleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSettingsScreen(onManageTypes: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.service_settings_title)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        })
    }) { padding ->
        ListItem(
            headlineContent = { Text(stringResource(R.string.service_manage_common_items)) },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(padding).clickable(onClick = onManageTypes),
        )
    }
}

@Composable
fun ServiceTypeManagementRoute(viewModel: ServiceTypeManagementViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServiceTypeManagementScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceTypeManagementScreen(
    state: ServiceTypeManagementUiState,
    onEvent: (ServiceTypeManagementEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.service_manage_common_items)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        })
    }) { padding ->
        val orderedTypes = state.types.filter { it.vehicleType == state.vehicleType }.sortedBy(ServiceType::sortOrder)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { androidx.compose.foundation.layout.Row { VehicleType.entries.forEach { type -> FilterChip(selected = state.vehicleType == type, onClick = { onEvent(ServiceTypeManagementEvent.SelectVehicleType(type)) }, label = { Text(stringResource(if (type == VehicleType.CAR) R.string.vehicle_type_car else R.string.vehicle_type_motorcycle)) }) } } }
            item {
                OutlinedButton(onClick = { onEvent(ServiceTypeManagementEvent.AddCustomType) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.service_add_custom_common_item))
                }
            }
            items(orderedTypes.size, key = { orderedTypes[it].id }) { index ->
                val type = orderedTypes[index]
                ServiceTypeManageRow(type, index, orderedTypes.size, onEvent)
            }
        }
    }
    if (state.isEditorVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(ServiceTypeManagementEvent.DismissEditor) },
            title = { Text(stringResource(if (state.editingId == null) R.string.service_add_custom_common_item else R.string.edit_custom_service_type_title)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = state.name,
                    onValueChange = { onEvent(ServiceTypeManagementEvent.NameChanged(it)) },
                    label = { Text(stringResource(R.string.service_custom_type_name)) },
                    singleLine = true,
                )
            },
            confirmButton = { TextButton(onClick = { onEvent(ServiceTypeManagementEvent.Save) }) { Text(stringResource(R.string.save_changes)) } },
            dismissButton = { TextButton(onClick = { onEvent(ServiceTypeManagementEvent.DismissEditor) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ServiceTypeManageRow(type: ServiceType, index: Int, count: Int, onEvent: (ServiceTypeManagementEvent) -> Unit) {
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(type.name)
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { onEvent(ServiceTypeManagementEvent.Move(type.id, -1)) }, enabled = index > 0) { Text(stringResource(R.string.move_up)) }
                TextButton(onClick = { onEvent(ServiceTypeManagementEvent.Move(type.id, 1)) }, enabled = index < count - 1) { Text(stringResource(R.string.move_down)) }
                if (!type.isBuiltIn) TextButton(onClick = { onEvent(ServiceTypeManagementEvent.EditCustomType(type)) }) { Text(stringResource(R.string.edit)) }
                if (!type.isBuiltIn) TextButton(onClick = { onEvent(ServiceTypeManagementEvent.DeleteCustomType(type.id)) }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = { onEvent(ServiceTypeManagementEvent.SetArchived(type.id, !type.isArchived)) }) {
                    Text(stringResource(if (type.isArchived) R.string.show_again else R.string.hide))
                }
            }
        }
    }
}
