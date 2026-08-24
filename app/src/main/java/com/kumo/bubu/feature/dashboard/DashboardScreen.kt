package com.kumo.bubu.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.toFuelEconomyDisplayText

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onAddVehicle: () -> Unit,
    onOpenVehicleHistory: (Long) -> Unit,
    onAddFuel: (Long) -> Unit,
    onAddService: (Long) -> Unit,
    onOpenReminders: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(state, onAddVehicle, onOpenVehicleHistory, onAddFuel, onAddService, onOpenReminders)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAddVehicle: () -> Unit,
    onOpenVehicleHistory: (Long) -> Unit,
    onAddFuel: (Long) -> Unit,
    onAddService: (Long) -> Unit,
    onOpenReminders: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedVehicleId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedVehicle = state.vehicles.firstOrNull { it.vehicleId == selectedVehicleId }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_garage_title)) },
                actions = {
                    IconButton(onClick = onOpenReminders) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.reminders_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> DashboardStatus(padding, loading = true)
            state.loadFailed -> DashboardStatus(padding, loading = false)
            state.vehicles.isEmpty() -> DashboardEmptyState(onAddVehicle, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.vehicles, key = VehicleDashboardItem::vehicleId) { vehicle ->
                    VehicleDashboardCard(
                        vehicle = vehicle,
                        onClick = { onOpenVehicleHistory(vehicle.vehicleId) },
                        onAddRecord = { selectedVehicleId = vehicle.vehicleId },
                    )
                }
            }
        }
    }

    selectedVehicle?.let { vehicle ->
        AddVehicleRecordSheet(
            vehicleName = vehicle.name,
            onDismiss = { selectedVehicleId = null },
            onAddFuel = {
                selectedVehicleId = null
                onAddFuel(vehicle.vehicleId)
            },
            onAddService = {
                selectedVehicleId = null
                onAddService(vehicle.vehicleId)
            },
        )
    }
}

@Composable
fun VehicleDashboardCard(
    vehicle: VehicleDashboardItem,
    onClick: () -> Unit,
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VehicleTypeIcon(vehicle.vehicleType)
                Column(modifier = Modifier.weight(1f)) {
                    Text(vehicle.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = vehicle.licensePlate ?: stringResource(R.string.dashboard_license_plate_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick = onAddRecord,
                    modifier = Modifier.testTag("dashboard-add-${vehicle.vehicleId}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.dashboard_add_record),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VehicleMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    value = stringResource(R.string.dashboard_odometer_value, vehicle.latestOdometerKm),
                    label = stringResource(R.string.dashboard_latest_odometer),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                VehicleMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.LocalGasStation,
                    value = vehicle.averageFuelEconomyMilliKmPerLiter
                        ?.toFuelEconomyDisplayText()
                        ?.let { value -> stringResource(R.string.dashboard_fuel_economy_value, value) }
                        ?: stringResource(R.string.dashboard_fuel_economy_unavailable),
                    label = stringResource(R.string.dashboard_average_fuel_economy),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
            vehicle.maintenanceRemainingKm?.let { remainingKm ->
                Text(
                    text = vehicle.maintenanceEstimatedDays?.let { days ->
                        stringResource(
                            R.string.dashboard_maintenance_remaining_with_days,
                            remainingKm.coerceAtLeast(0L),
                            days,
                        )
                    } ?: stringResource(
                        R.string.dashboard_maintenance_remaining_insufficient,
                        remainingKm.coerceAtLeast(0L),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = vehicle.fuelPredictionDays?.let { days ->
                    stringResource(R.string.dashboard_fuel_prediction, days)
                } ?: stringResource(R.string.dashboard_fuel_prediction_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VehicleMetric(
    icon: ImageVector,
    value: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DashboardEmptyState(
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.dashboard_empty_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.dashboard_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddVehicle) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_vehicle), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleRecordSheet(
    vehicleName: String,
    onDismiss: () -> Unit,
    onAddFuel: () -> Unit,
    onAddService: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text(
                text = stringResource(R.string.dashboard_add_record_title, vehicleName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.dashboard_add_fuel)) },
                leadingContent = { Icon(Icons.Filled.LocalGasStation, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onAddFuel),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.dashboard_add_service)) },
                leadingContent = { Icon(Icons.Filled.Build, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onAddService),
            )
        }
    }
}

@Composable
private fun DashboardStatus(padding: PaddingValues, loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = stringResource(R.string.dashboard_load_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun VehicleTypeIcon(type: VehicleType) {
    Icon(
        imageVector = if (type == VehicleType.CAR) Icons.Filled.DirectionsCar else Icons.Filled.TwoWheeler,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}
