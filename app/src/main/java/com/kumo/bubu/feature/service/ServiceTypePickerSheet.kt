package com.kumo.bubu.feature.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R

@Composable
internal fun ServiceCustomItemDialog(state: ServiceFormUiState, onEvent: (ServiceFormEvent) -> Unit) {
    if (!state.isCustomItemDialogVisible) return
    AlertDialog(
        onDismissRequest = { onEvent(ServiceFormEvent.CloseCustomItemDialog) },
        title = { Text(stringResource(R.string.service_custom_item_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.customItemName,
                    onValueChange = { onEvent(ServiceFormEvent.CustomItemNameChanged(it)) },
                    label = { Text(stringResource(R.string.service_item_name)) },
                    isError = state.error == ServiceFormError.CUSTOM_TYPE_NAME_REQUIRED,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.saveCustomItemAsType,
                        onCheckedChange = { onEvent(ServiceFormEvent.SaveCustomItemAsTypeChanged(it)) },
                    )
                    Text(stringResource(R.string.service_save_custom_as_common))
                }
                state.error.takeIf {
                    it == ServiceFormError.CUSTOM_TYPE_NAME_REQUIRED || it == ServiceFormError.TYPE_SAVE_FAILED
                }?.let { ServiceFormErrorMessage(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ServiceFormEvent.AddCustomItem) }, enabled = !state.isSaving) {
                Text(stringResource(R.string.service_add_custom_item))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ServiceFormEvent.CloseCustomItemDialog) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceItemPickerScreen(state: ServiceFormUiState, onEvent: (ServiceFormEvent) -> Unit) {
    val vehicleType = state.selectedVehicle?.vehicleType
    var showAllItems by rememberSaveable(vehicleType) { mutableStateOf(false) }
    val availableTypes = state.types
        .filter { !it.isArchived && it.vehicleType == vehicleType }
        .sortedBy { it.sortOrder }
    val displayedTypes = if (showAllItems) availableTypes else availableTypes.take(8)
    androidx.compose.material3.Scaffold(topBar = {
        androidx.compose.material3.TopAppBar(
            title = { Text(stringResource(R.string.service_parts_items_title)) },
            navigationIcon = { TextButton(onClick = { onEvent(ServiceFormEvent.CloseItemPicker) }) { Text(stringResource(R.string.back)) } },
        )
    }) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item { androidx.compose.material3.OutlinedButton(onClick = { onEvent(ServiceFormEvent.OpenCustomItemDialog) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.service_add_custom_item_short)) } }
            items(displayedTypes, key = { it.id }) { type ->
                androidx.compose.material3.ListItem(headlineContent = { Text(type.name) }, modifier = Modifier.clickable { onEvent(ServiceFormEvent.AddTypeItem(type)) })
            }
            if (!showAllItems && availableTypes.size > displayedTypes.size) {
                item {
                    TextButton(onClick = { showAllItems = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.service_more_items))
                    }
                }
            }
        }
    }
}
