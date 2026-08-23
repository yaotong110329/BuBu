package com.kumo.bubu.feature.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ServiceType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServiceItemsSection(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.service_parts_items_title), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { onEvent(ServiceFormEvent.OpenItemPicker) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.service_add_item)) }

        Text(stringResource(R.string.service_added_items), style = MaterialTheme.typography.titleMedium)
        if (state.items.isEmpty()) {
            Text(
                stringResource(R.string.service_items_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            state.items.forEach { item -> key(item.draftKey) { ServiceItemCard(item, state.isSaving, onEvent) } }
        }
        state.error.takeIf { it == ServiceFormError.ITEM_REQUIRED }?.let { ServiceFormErrorMessage(it) }
    }
}

@Composable
internal fun ServiceWorkOrderTotal(state: ServiceFormUiState) {
    val total = state.calculatedItemsTotalTwd
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.service_total_cost), style = MaterialTheme.typography.titleMedium)
            Text(
                total?.let { stringResource(R.string.service_twd_amount, it) }
                    ?: stringResource(R.string.service_amount_too_large),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ServiceItemCard(item: ServiceItemDraft, isSaving: Boolean, onEvent: (ServiceFormEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isSaving) {
            onEvent(ServiceFormEvent.EditItem(item.draftKey))
        },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name.ifBlank { stringResource(R.string.service_item_unnamed) }, style = MaterialTheme.typography.titleSmall)
            Text(
                item.amountTwd?.let { stringResource(R.string.service_twd_amount, it) }
                    ?: stringResource(R.string.service_item_amount_unset),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onEvent(ServiceFormEvent.EditItem(item.draftKey)) }, enabled = !isSaving) {
                    Text(stringResource(R.string.edit))
                }
                TextButton(onClick = { onEvent(ServiceFormEvent.RemoveItem(item.draftKey)) }, enabled = !isSaving) {
                    Text(stringResource(R.string.remove_service_item))
                }
            }
        }
    }
}

@Composable
private fun serviceTypeIcon(type: ServiceType): ImageVector = when {
    type.publicId.contains("battery") -> Icons.Filled.BatteryFull
    type.publicId.contains("tire") -> Icons.Filled.DirectionsCar
    type.publicId.contains("oil") -> Icons.Filled.Settings
    else -> Icons.Filled.Build
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceItemEditorSheet(state: ServiceFormUiState, onEvent: (ServiceFormEvent) -> Unit) {
    val item = state.itemEditorDraft ?: return
    ModalBottomSheet(onDismissRequest = { onEvent(ServiceFormEvent.CloseItemEditor) }) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.edit_service_item_title), style = MaterialTheme.typography.headlineSmall)
            ServiceTextField(item.name, { onEvent(ServiceFormEvent.ItemNameChanged(item.draftKey, it)) }, R.string.service_item_name, enabled = !state.isSaving)
            ServiceTextField(item.amount, { onEvent(ServiceFormEvent.ItemAmountChanged(item.draftKey, it)) }, R.string.service_item_amount, keyboardType = KeyboardType.Number, error = state.error.takeIf { it == ServiceFormError.ITEM_AMOUNT_INVALID }, enabled = !state.isSaving)
            ServiceTextField(item.note, { onEvent(ServiceFormEvent.ItemNoteChanged(item.draftKey, it)) }, R.string.service_item_note, minLines = 2, enabled = !state.isSaving)
            Button(onClick = { onEvent(ServiceFormEvent.CompleteItemEditor) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
