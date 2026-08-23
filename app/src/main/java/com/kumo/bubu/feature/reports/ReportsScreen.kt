package com.kumo.bubu.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ReportCard
import com.kumo.bubu.domain.model.ReportSource

@Composable
fun ReportsRoute(
    viewModel: ReportsViewModel,
    onOpenSource: (ReportSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    ReportsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSource = onOpenSource,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReportsScreen(
    state: ReportsUiState,
    onEvent: (ReportsEvent) -> Unit,
    onOpenSource: (ReportSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                actions = {
                    TextButton(onClick = { onEvent(ReportsEvent.OpenLayoutEditor) }) {
                        Text(stringResource(R.string.reports_customize))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> ReportsMessage(R.string.reports_title, Modifier.padding(padding))
            state.loadFailed || state.vehicles.isEmpty() ->
                ReportsMessage(R.string.reports_no_data, Modifier.padding(padding))
            else -> ReportsContent(state, onEvent, onOpenSource, padding)
        }
        if (state.isLayoutEditorVisible) ReportLayoutEditor(state.layout, onEvent)
    }
}

@Composable
private fun ReportsContent(
    state: ReportsUiState,
    onEvent: (ReportsEvent) -> Unit,
    onOpenSource: (ReportSource) -> Unit,
    padding: PaddingValues,
) {
    val report = state.report
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.testTag("reports-scroll-list"),
    ) {
        item { ReportControls(state, onEvent) }
        if (report != null) {
            state.layout.visibleCards.forEach { card ->
                item(key = card) {
                    ReportCardContent(card, report, onEvent, onOpenSource)
                }
            }
        }
    }
}

@Composable
private fun ReportCardContent(
    card: ReportCard,
    report: ReportPresentation,
    onEvent: (ReportsEvent) -> Unit,
    onOpenSource: (ReportSource) -> Unit,
) {
    when (card) {
        ReportCard.TOTAL_COST -> MonthlyCostChart(report, onEvent)
        ReportCard.FUEL_ECONOMY -> FuelEconomyChart(report, onOpenSource)
        ReportCard.COST_PER_KM -> CostPerKmChart(report)
        ReportCard.CATEGORY -> CategoryChart(report)
        ReportCard.SERVICE_COST -> ServiceCostChart(report)
        ReportCard.MILEAGE -> MileageChart(report, onOpenSource)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportLayoutEditor(layout: com.kumo.bubu.domain.model.ReportLayout, onEvent: (ReportsEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(ReportsEvent.CloseLayoutEditor) }) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.reports_customize_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.reports_customize_description), style = MaterialTheme.typography.bodySmall)
            layout.orderedCards.forEachIndexed { index, card ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(card.labelRes()), modifier = Modifier.weight(1f))
                    Switch(
                        checked = card !in layout.hiddenCards,
                        onCheckedChange = { visible -> onEvent(ReportsEvent.SetCardVisible(card, visible)) },
                    )
                    TextButton(
                        enabled = index > 0,
                        onClick = { onEvent(ReportsEvent.MoveCard(card, -1)) },
                    ) { Text(stringResource(R.string.reports_move_up)) }
                    TextButton(
                        enabled = index < layout.orderedCards.lastIndex,
                        onClick = { onEvent(ReportsEvent.MoveCard(card, 1)) },
                    ) { Text(stringResource(R.string.reports_move_down)) }
                }
            }
            TextButton(
                onClick = { onEvent(ReportsEvent.CloseLayoutEditor) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun ReportControls(state: ReportsUiState, onEvent: (ReportsEvent) -> Unit) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.reports_vehicle_filter), style = MaterialTheme.typography.labelLarge)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("reports-vehicle-selector"),
        ) {
            items(state.vehicles, key = ReportVehicleOption::id) { vehicle ->
                FilterChip(
                    selected = state.selectedVehicleId == vehicle.id,
                    onClick = { onEvent(ReportsEvent.SelectVehicle(vehicle.id)) },
                    label = { Text(vehicle.name) },
                    modifier = Modifier.testTag("reports-vehicle-${vehicle.id}"),
                )
            }
        }
        Text(stringResource(R.string.reports_period_filter), style = MaterialTheme.typography.labelLarge)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("reports-period-selector"),
        ) {
            items(ReportPeriod.entries) { period ->
                FilterChip(
                    selected = state.period == period,
                    onClick = { onEvent(ReportsEvent.SelectPeriod(period)) },
                    label = { Text(stringResource(period.labelRes())) },
                    modifier = Modifier.testTag("reports-period-${period.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
internal fun ReportsMessage(stringRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(stringRes),
        modifier = modifier.padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun ReportPeriod.labelRes(): Int = when (this) {
    ReportPeriod.LAST_6_MONTHS -> R.string.reports_period_last_6_months
    ReportPeriod.LAST_1_YEAR -> R.string.reports_period_last_1_year
    ReportPeriod.LAST_2_YEARS -> R.string.reports_period_last_2_years
    ReportPeriod.ALL -> R.string.reports_period_all
}

private fun ReportCard.labelRes(): Int = when (this) {
    ReportCard.TOTAL_COST -> R.string.reports_total_cost
    ReportCard.FUEL_ECONOMY -> R.string.reports_fuel_economy
    ReportCard.COST_PER_KM -> R.string.reports_cost_per_km
    ReportCard.CATEGORY -> R.string.reports_category_breakdown
    ReportCard.SERVICE_COST -> R.string.reports_service_cost
    ReportCard.MILEAGE -> R.string.reports_mileage
}
