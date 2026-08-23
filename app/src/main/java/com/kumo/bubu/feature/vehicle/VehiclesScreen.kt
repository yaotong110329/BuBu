package com.kumo.bubu.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.ConfirmDeleteDialog
import com.kumo.bubu.core.ui.components.EmptyState
import com.kumo.bubu.domain.model.Vehicle

@Composable
fun VehiclesRoute(
    viewModel: VehiclesViewModel,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VehiclesScreen(uiState, viewModel::onEvent, onAddVehicle, onEditVehicle)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VehiclesScreen(
    uiState: VehiclesUiState,
    onEvent: (VehiclesEvent) -> Unit,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.vehicles_title)) }) },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.loadFailed && uiState.vehicles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddVehicle,
                    text = { Text(stringResource(R.string.add_vehicle)) },
                    icon = { Text(stringResource(R.string.add_symbol)) },
                )
            }
        },
    ) { padding -> VehicleBody(uiState, onEvent, onAddVehicle, onEditVehicle, padding) }

    DeleteConfirmation(uiState, onEvent)
}

@Composable
private fun VehicleBody(
    state: VehiclesUiState,
    onEvent: (VehiclesEvent) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    padding: PaddingValues,
) {
    when {
        state.isLoading -> CenteredVehicleStatus(padding, true)
        state.loadFailed -> CenteredVehicleStatus(padding, false)
        state.vehicles.isEmpty() -> EmptyState(
            title = stringResource(R.string.no_vehicles_title),
            actionLabel = stringResource(R.string.add_first_vehicle),
            onAction = onAdd,
            modifier = Modifier.padding(padding),
        )
        else -> VehicleList(state, onEvent, onEdit, padding)
    }
}

@Composable
private fun CenteredVehicleStatus(padding: PaddingValues, loading: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(
            stringResource(if (loading) R.string.loading_vehicles else R.string.vehicle_load_error),
            modifier = Modifier.padding(top = 12.dp),
            color = if (loading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun VehicleList(
    state: VehiclesUiState,
    onEvent: (VehiclesEvent) -> Unit,
    onEdit: (Long) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.actionError?.let { error ->
            item {
                TextButton(onClick = { onEvent(VehiclesEvent.DismissError) }) {
                    Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        items(state.vehicles, key = Vehicle::id) { vehicle ->
            VehicleCard(
                vehicle = vehicle,
                isCurrent = vehicle.publicId == state.currentVehiclePublicId,
                isBusy = vehicle.id == state.actionInProgressVehicleId,
                onEvent = onEvent,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: Vehicle,
    isCurrent: Boolean,
    isBusy: Boolean,
    onEvent: (VehiclesEvent) -> Unit,
    onEdit: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                if (isCurrent) Text(stringResource(R.string.current_vehicle), color = MaterialTheme.colorScheme.primary)
                if (vehicle.isArchived) Text(stringResource(R.string.archived_vehicle), color = MaterialTheme.colorScheme.secondary)
            }
            Text(stringResource(vehicle.vehicleType.labelRes()), style = MaterialTheme.typography.bodyMedium)
            vehicle.brandAndModel()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.current_odometer_value, vehicle.currentOdometerKm))
            if (!vehicle.isArchived && !isCurrent) {
                TextButton(
                    enabled = !isBusy,
                    onClick = { onEvent(VehiclesEvent.SelectCurrent(vehicle.publicId, vehicle.id)) },
                ) { Text(stringResource(R.string.set_current_vehicle)) }
            }
            VehicleActions(vehicle, isBusy, onEvent, onEdit)
        }
    }
}

@Composable
private fun VehicleActions(
    vehicle: Vehicle,
    isBusy: Boolean,
    onEvent: (VehiclesEvent) -> Unit,
    onEdit: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(enabled = !isBusy, onClick = { onEdit(vehicle.id) }) { Text(stringResource(R.string.edit)) }
        TextButton(
            enabled = !isBusy,
            onClick = {
                onEvent(if (vehicle.isArchived) VehiclesEvent.Unarchive(vehicle.id) else VehiclesEvent.Archive(vehicle.id))
            },
        ) { Text(stringResource(if (vehicle.isArchived) R.string.unarchive else R.string.archive)) }
        TextButton(enabled = !isBusy, onClick = { onEvent(VehiclesEvent.RequestDelete(vehicle.id)) }) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeleteConfirmation(state: VehiclesUiState, onEvent: (VehiclesEvent) -> Unit) {
    val vehicle = state.vehicles.firstOrNull { it.id == state.deleteConfirmationVehicleId } ?: return
    ConfirmDeleteDialog(
        title = stringResource(R.string.delete_vehicle_title),
        message = stringResource(R.string.delete_vehicle_message, vehicle.name),
        confirmLabel = stringResource(R.string.delete),
        cancelLabel = stringResource(R.string.cancel),
        onConfirm = { onEvent(VehiclesEvent.ConfirmDelete) },
        onDismiss = { onEvent(VehiclesEvent.DismissDelete) },
    )
}

private fun Vehicle.brandAndModel(): String? = listOfNotNull(brand, model).joinToString(" ").ifBlank { null }

private fun VehicleActionError.messageRes(): Int = when (this) {
    VehicleActionError.SELECT_FAILED -> R.string.current_vehicle_save_error
    VehicleActionError.UPDATE_FAILED -> R.string.vehicle_update_error
    VehicleActionError.DELETE_FAILED -> R.string.vehicle_delete_error
}
