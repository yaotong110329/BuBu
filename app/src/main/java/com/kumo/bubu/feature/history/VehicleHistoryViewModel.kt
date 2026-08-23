package com.kumo.bubu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.calculateFuelEconomyMilliKmPerLiterByRecord
import com.kumo.bubu.domain.repository.FuelRepository
import com.kumo.bubu.domain.repository.ServiceRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryFilter { ALL, FUEL, MAINTENANCE }

sealed interface VehicleHistoryItem {
    val id: Long
    val dateEpochDay: Long
    val timeMinuteOfDay: Int?
    val sequenceInDay: Int

    data class Fuel(
        val record: FuelRecord,
        val fuelEconomyMilliKmPerLiter: Long?,
    ) : VehicleHistoryItem {
        override val id: Long = record.id
        override val dateEpochDay: Long = record.dateEpochDay
        override val timeMinuteOfDay: Int? = record.timeMinuteOfDay
        override val sequenceInDay: Int = record.sequenceInDay
    }

    data class Maintenance(
        val details: ServiceRecordDetails,
        val primaryItemName: String,
    ) : VehicleHistoryItem {
        override val id: Long = details.record.id
        override val dateEpochDay: Long = details.record.dateEpochDay
        override val timeMinuteOfDay: Int? = details.record.timeMinuteOfDay
        override val sequenceInDay: Int = details.record.sequenceInDay
    }
}

data class VehicleHistoryUiState(
    val vehicle: Vehicle? = null,
    val records: List<VehicleHistoryItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val searchQuery: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val hasInvalidDateRange: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val deleteConfirmation: VehicleHistoryItem? = null,
    val deleteInProgress: Boolean = false,
    val deleteFailed: Boolean = false,
)

sealed interface VehicleHistoryEvent {
    data class FilterChanged(val filter: HistoryFilter) : VehicleHistoryEvent
    data class SearchChanged(val value: String) : VehicleHistoryEvent
    data class StartDateChanged(val value: String) : VehicleHistoryEvent
    data class EndDateChanged(val value: String) : VehicleHistoryEvent
    data class RequestDelete(val item: VehicleHistoryItem) : VehicleHistoryEvent
    data object ConfirmDelete : VehicleHistoryEvent
    data object DismissDelete : VehicleHistoryEvent
    data object DismissDeleteError : VehicleHistoryEvent
}

private data class HistoryQuery(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val searchQuery: String = "",
    val startDate: String = "",
    val endDate: String = "",
)

private data class HistoryActions(
    val deleteConfirmation: VehicleHistoryItem? = null,
    val deleteInProgress: Boolean = false,
    val deleteFailed: Boolean = false,
)

class VehicleHistoryViewModel(
    private val vehicleId: Long,
    vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {
    private val query = MutableStateFlow(HistoryQuery())
    private val actions = MutableStateFlow(HistoryActions())

    private val historyData = combine(
        vehicleRepository.observeVehicles(),
        fuelRepository.observeFuelRecords(vehicleId),
        serviceRepository.observeServiceRecordDetails(vehicleId),
    ) { vehicles, fuelRecords, serviceDetails ->
        val economyByRecordId = calculateFuelEconomyMilliKmPerLiterByRecord(fuelRecords)
        VehicleHistoryUiState(
            vehicle = vehicles.firstOrNull { it.id == vehicleId },
            records = buildHistoryItems(fuelRecords, serviceDetails, economyByRecordId),
            isLoading = false,
        )
    }.catch { emit(VehicleHistoryUiState(isLoading = false, loadFailed = true)) }

    val uiState = combine(historyData, query, actions) { data, currentQuery, currentActions ->
        data.copy(
            records = data.records.filterFor(currentQuery),
            filter = currentQuery.filter,
            searchQuery = currentQuery.searchQuery,
            startDate = currentQuery.startDate,
            endDate = currentQuery.endDate,
            hasInvalidDateRange = currentQuery.hasInvalidDateRange(),
            deleteConfirmation = currentActions.deleteConfirmation,
            deleteInProgress = currentActions.deleteInProgress,
            deleteFailed = currentActions.deleteFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VehicleHistoryUiState(),
    )

    fun onEvent(event: VehicleHistoryEvent) {
        when (event) {
            is VehicleHistoryEvent.FilterChanged -> query.update { it.copy(filter = event.filter) }
            is VehicleHistoryEvent.SearchChanged -> query.update { it.copy(searchQuery = event.value) }
            is VehicleHistoryEvent.StartDateChanged -> query.update { it.copy(startDate = event.value) }
            is VehicleHistoryEvent.EndDateChanged -> query.update { it.copy(endDate = event.value) }
            is VehicleHistoryEvent.RequestDelete -> actions.update {
                it.copy(deleteConfirmation = event.item, deleteFailed = false)
            }
            VehicleHistoryEvent.DismissDelete -> actions.update { it.copy(deleteConfirmation = null) }
            VehicleHistoryEvent.DismissDeleteError -> actions.update { it.copy(deleteFailed = false) }
            VehicleHistoryEvent.ConfirmDelete -> deleteConfirmedRecord()
        }
    }

    private fun deleteConfirmedRecord() {
        val item = actions.value.deleteConfirmation ?: return
        if (actions.value.deleteInProgress) return
        actions.update { it.copy(deleteInProgress = true, deleteFailed = false) }
        viewModelScope.launch {
            runCatching {
                when (item) {
                    is VehicleHistoryItem.Fuel -> fuelRepository.deleteFuelRecord(item.record.id)
                    is VehicleHistoryItem.Maintenance -> serviceRepository.deleteServiceRecord(item.details.record.id)
                }
            }.onSuccess {
                actions.value = HistoryActions()
            }.onFailure {
                actions.value = HistoryActions(deleteFailed = true)
            }
        }
    }

    companion object {
        fun factory(
            vehicleId: Long,
            vehicleRepository: VehicleRepository,
            fuelRepository: FuelRepository,
            serviceRepository: ServiceRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VehicleHistoryViewModel(
                    vehicleId,
                    vehicleRepository,
                    fuelRepository,
                    serviceRepository,
                )
            }
        }
    }
}

private fun buildHistoryItems(
    fuelRecords: List<FuelRecord>,
    serviceDetails: List<ServiceRecordDetails>,
    economyByRecordId: Map<Long, Long>,
): List<VehicleHistoryItem> = (
    fuelRecords.map { VehicleHistoryItem.Fuel(it, economyByRecordId[it.id]) } +
        serviceDetails.map { details ->
            VehicleHistoryItem.Maintenance(
                details = details,
                primaryItemName = details.items.minWithOrNull(
                    compareBy<com.kumo.bubu.domain.model.ServiceItem> { it.sequenceInRecord }.thenBy { it.id },
                )?.nameSnapshot ?: details.record.title,
            )
        }
    ).sortedWith(
    compareByDescending<VehicleHistoryItem> { it.dateEpochDay }
        .thenByDescending { it.timeMinuteOfDay ?: -1 }
        .thenByDescending { it.sequenceInDay }
        .thenByDescending { it.id }
        .thenBy { if (it is VehicleHistoryItem.Fuel) 0 else 1 },
)

private fun List<VehicleHistoryItem>.filterFor(query: HistoryQuery): List<VehicleHistoryItem> {
    val normalizedQuery = query.searchQuery.trim().lowercase(Locale.ROOT)
    return filter { item ->
        item.matchesFilter(query.filter) &&
            item.matchesDateRange(query) &&
            (normalizedQuery.isEmpty() || item.matchesSearch(normalizedQuery))
    }
}

private fun HistoryQuery.hasInvalidDateRange(): Boolean {
    val start = startDate.trim().takeIf(String::isNotEmpty)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val end = endDate.trim().takeIf(String::isNotEmpty)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    return (startDate.isNotBlank() && start == null) || (endDate.isNotBlank() && end == null) || (start != null && end != null && start > end)
}

private fun VehicleHistoryItem.matchesDateRange(query: HistoryQuery): Boolean {
    if (query.hasInvalidDateRange()) return true
    val start = query.startDate.trim().takeIf(String::isNotEmpty)?.let { java.time.LocalDate.parse(it).toEpochDay() }
    val end = query.endDate.trim().takeIf(String::isNotEmpty)?.let { java.time.LocalDate.parse(it).toEpochDay() }
    return (start == null || dateEpochDay >= start) && (end == null || dateEpochDay <= end)
}

private fun VehicleHistoryItem.matchesFilter(filter: HistoryFilter): Boolean = when (filter) {
    HistoryFilter.ALL -> true
    HistoryFilter.FUEL -> this is VehicleHistoryItem.Fuel
    HistoryFilter.MAINTENANCE -> this is VehicleHistoryItem.Maintenance
}

private fun VehicleHistoryItem.matchesSearch(query: String): Boolean = when (this) {
    is VehicleHistoryItem.Fuel -> listOfNotNull(
        record.fuelProduct?.searchLabel(),
        record.note,
    ).any { it.lowercase(Locale.ROOT).contains(query) }

    is VehicleHistoryItem.Maintenance -> (
        listOf(details.record.title, details.record.note.orEmpty()) + details.items.map { it.nameSnapshot }
        ).any { it.lowercase(Locale.ROOT).contains(query) }
}

private fun FuelProduct.searchLabel(): String = name
