package com.kumo.bubu.feature.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
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
    val listState = rememberLazyListState()
    val activeTypes = state.types
        .filter { it.vehicleType == state.vehicleType && !it.isArchived }
        .sortedBy(ServiceType::sortOrder)
    val activeTypeIds = activeTypes.map(ServiceType::id)
    var orderedTypeIds by remember(state.vehicleType, activeTypeIds) { mutableStateOf(activeTypeIds) }
    val typesById = activeTypes.associateBy(ServiceType::id)
    val orderedTypes = orderedTypeIds.mapNotNull(typesById::get)
    var draggedTypeId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartOrder by remember { mutableStateOf(emptyList<Long>()) }

    fun resetDrag() {
        draggedTypeId = null
        dragOffsetY = 0f
    }

    fun finishReorder(commit: Boolean) {
        val draggedId = draggedTypeId ?: return
        if (commit && dragStartOrder != orderedTypeIds) {
            onEvent(ServiceTypeManagementEvent.Reorder(orderedTypeIds))
        } else if (!commit) {
            orderedTypeIds = dragStartOrder
        }
        resetDrag()
    }

    fun moveDraggedTypeTo(targetId: Long) {
        val draggedId = draggedTypeId ?: return
        if (targetId == draggedId) return
        val draggedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId } ?: return
        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == targetId } ?: return
        val updatedOrder = orderedTypeIds.moveServiceTypeTo(draggedId, targetId)
        if (updatedOrder != orderedTypeIds) {
            // The item changes its layout slot here. Offset by the same amount so it stays below the finger.
            dragOffsetY += (draggedItem.offset - targetItem.offset).toFloat()
            orderedTypeIds = updatedOrder
        }
    }

    fun updateDrag(deltaY: Float) {
        val draggedId = draggedTypeId ?: return
        dragOffsetY += deltaY
        val draggedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId } ?: return
        val draggedCenterY = draggedItem.offset + draggedItem.size / 2 + dragOffsetY
        val targetId = listState.layoutInfo.visibleItemsInfo
            .filter { it.key is Long }
            .minByOrNull { item ->
                abs((item.offset + item.size / 2).toFloat() - draggedCenterY)
            }
            ?.key as? Long
        if (targetId != null) moveDraggedTypeTo(targetId)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.service_manage_common_items)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { androidx.compose.foundation.layout.Row { VehicleType.entries.forEach { type -> FilterChip(selected = state.vehicleType == type, onClick = { onEvent(ServiceTypeManagementEvent.SelectVehicleType(type)) }, label = { Text(stringResource(if (type == VehicleType.CAR) R.string.vehicle_type_car else R.string.vehicle_type_motorcycle)) }) } } }
            item {
                OutlinedButton(onClick = { onEvent(ServiceTypeManagementEvent.AddCustomType) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.service_add_custom_common_item))
                }
            }
            if (state.vehicles.any { it.vehicleType == state.vehicleType }) {
                item {
                    Text(
                        text = stringResource(R.string.service_reminder_settings_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.vehicles.filter { it.vehicleType == state.vehicleType }.forEach { vehicle ->
                            FilterChip(
                                selected = state.selectedReminderVehicleId == vehicle.id,
                                onClick = { onEvent(ServiceTypeManagementEvent.SelectReminderVehicle(vehicle.id)) },
                                label = { Text(vehicle.name) },
                            )
                        }
                    }
                }
            }
            items(orderedTypes.size, key = { orderedTypes[it].id }) { index ->
                val type = orderedTypes[index]
                val isDragging = draggedTypeId == type.id
                ServiceTypeManageRow(
                    type = type,
                    position = index,
                    isDragging = isDragging,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    onDragStart = {
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == type.id }
                        if (item != null) {
                            draggedTypeId = type.id
                            dragStartOrder = orderedTypeIds
                            dragOffsetY = 0f
                        }
                    },
                    onDrag = ::updateDrag,
                    onDragFinished = { finishReorder(commit = true) },
                    onDragCancelled = { finishReorder(commit = false) },
                    onEvent = onEvent,
                ) {
                    onEvent(ServiceTypeManagementEvent.EditReminder(type))
                }
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
    state.reminderEditingType?.let { type ->
        AlertDialog(
            onDismissRequest = { onEvent(ServiceTypeManagementEvent.DismissReminderEditor) },
            title = { Text(stringResource(R.string.service_reminder_editor_title, type.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.service_reminder_enabled))
                        Switch(
                            checked = state.reminderEnabled,
                            onCheckedChange = { onEvent(ServiceTypeManagementEvent.ReminderEnabledChanged(it)) },
                        )
                    }
                    OutlinedTextField(
                        value = state.reminderIntervalKm,
                        onValueChange = { onEvent(ServiceTypeManagementEvent.ReminderIntervalChanged(it)) },
                        label = { Text(stringResource(R.string.service_reminder_interval_km)) },
                        supportingText = { Text(stringResource(R.string.service_reminder_interval_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.reminderBaseOdometerKm,
                        onValueChange = { onEvent(ServiceTypeManagementEvent.ReminderBaseOdometerChanged(it)) },
                        label = { Text(stringResource(R.string.service_reminder_base_odometer_km)) },
                        supportingText = { Text(stringResource(R.string.service_reminder_base_odometer_hint)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onEvent(ServiceTypeManagementEvent.SaveReminder) }) { Text(stringResource(R.string.save_changes)) } },
            dismissButton = { TextButton(onClick = { onEvent(ServiceTypeManagementEvent.DismissReminderEditor) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ServiceTypeManageRow(
    type: ServiceType,
    position: Int,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit,
    onEvent: (ServiceTypeManagementEvent) -> Unit,
    onEditReminder: () -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var previousPosition by remember { mutableIntStateOf(position) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val placementOffset = remember { Animatable(0f) }
    LaunchedEffect(position, rowHeightPx) {
        if (rowHeightPx > 0 && previousPosition != position) {
            placementOffset.snapTo((previousPosition - position) * rowHeightPx.toFloat())
            previousPosition = position
            placementOffset.animateTo(0f, animationSpec = tween(durationMillis = 180))
        }
    }
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .onSizeChanged { rowHeightPx = it.height }
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else placementOffset.value
            }
            .pointerInput(type.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = onDragFinished,
                    onDragCancel = onDragCancelled,
                )
            },
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.service_drag_to_reorder),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(type.name)
                Text(
                    text = stringResource(if (type.isBuiltIn) R.string.service_type_builtin_label else R.string.service_type_custom_label),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEditReminder) { Text(stringResource(R.string.service_reminder_edit)) }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                ) {
                    if (!type.isBuiltIn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                isMenuExpanded = false
                                onEvent(ServiceTypeManagementEvent.EditCustomType(type))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                isMenuExpanded = false
                                onEvent(ServiceTypeManagementEvent.DeleteCustomType(type.id))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hide)) },
                        onClick = {
                            isMenuExpanded = false
                            onEvent(ServiceTypeManagementEvent.SetArchived(type.id, true))
                        },
                    )
                }
            }
        }
    }
}
