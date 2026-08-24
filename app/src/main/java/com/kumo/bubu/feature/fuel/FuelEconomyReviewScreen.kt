package com.kumo.bubu.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.FuelEconomyOutlier
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.detectFuelEconomyOutlierCandidates
import com.kumo.bubu.domain.model.deviationPermille
import com.kumo.bubu.domain.model.toFuelEconomyDisplayText
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FuelEconomyReviewItem(
    val record: FuelRecord,
    val vehicleName: String,
    val outlier: FuelEconomyOutlier?,
)

data class FuelEconomyReviewUiState(
    val suspected: List<FuelEconomyReviewItem> = emptyList(),
    val excluded: List<FuelEconomyReviewItem> = emptyList(),
    val isLoading: Boolean = true,
    val actionFailed: Boolean = false,
)

sealed interface FuelEconomyReviewEvent {
    data class ConfirmIncluded(val recordId: Long) : FuelEconomyReviewEvent
    data class Exclude(val recordId: Long) : FuelEconomyReviewEvent
}

class FuelEconomyReviewViewModel(
    private val fuelRepository: FuelRepository,
    vehicleRepository: VehicleRepository,
) : ViewModel() {
    val uiState = combine(
        vehicleRepository.observeVehicles(),
        fuelRepository.observeRecentFuelRecords(),
    ) { vehicles, records ->
        val vehicleNames = vehicles.associate { it.id to it.name }
        val candidates = detectFuelEconomyOutlierCandidates(records).associateBy { it.fuelRecordId }
        val reviewItems = records.mapNotNull { record ->
            val outlier = candidates[record.id]?.outlier
            when {
                outlier != null || record.fuelEconomyStatisticsStatus == FuelEconomyStatisticsStatus.EXCLUDED ->
                    FuelEconomyReviewItem(record, vehicleNames[record.vehicleId] ?: "", outlier)
                else -> null
            }
        }
        FuelEconomyReviewUiState(
            suspected = reviewItems
                .filter { it.outlier != null && it.record.fuelEconomyStatisticsStatus == FuelEconomyStatisticsStatus.UNREVIEWED }
                .sortedWith(
                    compareByDescending<FuelEconomyReviewItem> { it.outlier?.deviationPermille() ?: 0L }
                        .thenByDescending { it.record.dateEpochDay }
                        .thenByDescending { it.record.id },
                ),
            excluded = reviewItems
                .filter { it.record.fuelEconomyStatisticsStatus == FuelEconomyStatisticsStatus.EXCLUDED }
                .sortedWith(compareByDescending<FuelEconomyReviewItem> { it.record.dateEpochDay }.thenByDescending { it.record.id }),
            isLoading = false,
        )
    }.catch { emit(FuelEconomyReviewUiState(isLoading = false, actionFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelEconomyReviewUiState())

    fun onEvent(event: FuelEconomyReviewEvent) {
        val status = when (event) {
            is FuelEconomyReviewEvent.ConfirmIncluded -> FuelEconomyStatisticsStatus.INCLUDED
            is FuelEconomyReviewEvent.Exclude -> FuelEconomyStatisticsStatus.EXCLUDED
        }
        val id = when (event) {
            is FuelEconomyReviewEvent.ConfirmIncluded -> event.recordId
            is FuelEconomyReviewEvent.Exclude -> event.recordId
        }
        viewModelScope.launch { runCatching { fuelRepository.setFuelEconomyStatisticsStatus(id, status) } }
    }

    companion object {
        fun factory(
            fuelRepository: FuelRepository,
            vehicleRepository: VehicleRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { FuelEconomyReviewViewModel(fuelRepository, vehicleRepository) }
        }
    }
}

@Composable
fun FuelEconomyReviewRoute(
    viewModel: FuelEconomyReviewViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FuelEconomyReviewScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEconomyReviewScreen(
    state: FuelEconomyReviewUiState,
    onEvent: (FuelEconomyReviewEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.fuel_economy_review_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.fuel_back_symbol)) } },
        )
    }) { padding ->
        when {
            state.isLoading -> Unit
            state.suspected.isEmpty() && state.excluded.isEmpty() -> FuelEconomyReviewEmpty(padding)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.suspected.isNotEmpty()) {
                    item { ReviewSectionTitle(stringResource(R.string.fuel_economy_review_suspected)) }
                    items(state.suspected.size, key = { state.suspected[it].record.id }) { index ->
                        SuspectedFuelEconomyCard(state.suspected[index], onEvent)
                    }
                }
                if (state.excluded.isNotEmpty()) {
                    item { ReviewSectionTitle(stringResource(R.string.fuel_economy_review_excluded)) }
                    items(state.excluded.size, key = { state.excluded[it].record.id }) { index ->
                        ExcludedFuelEconomyCard(state.excluded[index], onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelEconomyReviewEmpty(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.fuel_economy_review_empty), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ReviewSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SuspectedFuelEconomyCard(item: FuelEconomyReviewItem, onEvent: (FuelEconomyReviewEvent) -> Unit) {
    FuelEconomyReviewCard(item) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onEvent(FuelEconomyReviewEvent.Exclude(item.record.id)) }) {
                Text(stringResource(R.string.fuel_economy_review_exclude))
            }
            TextButton(onClick = { onEvent(FuelEconomyReviewEvent.ConfirmIncluded(item.record.id)) }) {
                Text(stringResource(R.string.fuel_economy_review_include))
            }
        }
    }
}

@Composable
private fun ExcludedFuelEconomyCard(item: FuelEconomyReviewItem, onEvent: (FuelEconomyReviewEvent) -> Unit) {
    FuelEconomyReviewCard(item) {
        TextButton(onClick = { onEvent(FuelEconomyReviewEvent.ConfirmIncluded(item.record.id)) }) {
            Text(stringResource(R.string.fuel_economy_review_restore))
        }
    }
}

@Composable
private fun FuelEconomyReviewCard(item: FuelEconomyReviewItem, actions: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.vehicleName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(LocalDate.ofEpochDay(item.record.dateEpochDay).toString(), style = MaterialTheme.typography.bodySmall)
            item.outlier?.let { outlier ->
                Text(
                    stringResource(R.string.fuel_economy_review_value, outlier.candidateMilliKmPerLiter.toFuelEconomyDisplayText()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (item.record.fuelEconomyStatisticsStatus == FuelEconomyStatisticsStatus.EXCLUDED) {
                Text(stringResource(R.string.fuel_economy_review_excluded_summary), color = MaterialTheme.colorScheme.error)
            }
            actions()
        }
    }
}
