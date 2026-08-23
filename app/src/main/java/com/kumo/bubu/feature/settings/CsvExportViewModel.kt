package com.kumo.bubu.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.CsvExportRequest
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.repository.CsvExportRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CsvExportVehicleOption(
    val id: Long,
    val name: String,
    val isArchived: Boolean,
)

data class CsvExportUiState(
    val vehicles: List<CsvExportVehicleOption> = emptyList(),
    val includeAllVehicles: Boolean = true,
    val selectedVehicleIds: Set<Long> = emptySet(),
    val startDate: String = "",
    val endDate: String = "",
    val error: CsvExportError? = null,
    val isExporting: Boolean = false,
    val exportedFileName: String? = null,
)

enum class CsvExportError {
    INVALID_DATE_RANGE,
    NO_VEHICLE_SELECTED,
    WRITE_FAILED,
}

sealed interface CsvExportEvent {
    data class SetIncludeAllVehicles(val value: Boolean) : CsvExportEvent
    data class ToggleVehicle(val vehicleId: Long) : CsvExportEvent
    data class ChangeStartDate(val value: String) : CsvExportEvent
    data class ChangeEndDate(val value: String) : CsvExportEvent
    data object ClearResult : CsvExportEvent
}

class CsvExportViewModel(
    vehicleRepository: VehicleRepository,
    private val csvExportRepository: CsvExportRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(CsvExportControls())
    private val writeState = MutableStateFlow(CsvExportWriteState())

    val uiState = combine(
        vehicleRepository.observeVehicles(),
        controls,
        writeState,
    ) { vehicles, exportControls, exportWriteState ->
        val options = vehicles.map(Vehicle::toCsvExportOption)
        val validSelectedIds = exportControls.selectedVehicleIds.intersect(options.mapTo(mutableSetOf()) { it.id })
        CsvExportUiState(
            vehicles = options,
            includeAllVehicles = exportControls.includeAllVehicles,
            selectedVehicleIds = validSelectedIds,
            startDate = exportControls.startDate,
            endDate = exportControls.endDate,
            error = exportWriteState.error,
            isExporting = exportWriteState.isExporting,
            exportedFileName = exportWriteState.exportedFileName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CsvExportUiState(),
    )

    fun onEvent(event: CsvExportEvent) {
        controls.value = when (event) {
            is CsvExportEvent.SetIncludeAllVehicles -> controls.value.copy(includeAllVehicles = event.value)
            is CsvExportEvent.ToggleVehicle -> controls.value.copy(
                includeAllVehicles = false,
                selectedVehicleIds = controls.value.selectedVehicleIds.toggle(event.vehicleId),
            )
            is CsvExportEvent.ChangeStartDate -> controls.value.copy(startDate = event.value)
            is CsvExportEvent.ChangeEndDate -> controls.value.copy(endDate = event.value)
            CsvExportEvent.ClearResult -> {
                writeState.value = writeState.value.copy(error = null, exportedFileName = null)
                controls.value
            }
        }
    }

    fun createRequest(): CsvExportRequest? {
        val currentControls = controls.value
        val start = currentControls.startDate.toEpochDayOrNull()
        val end = currentControls.endDate.toEpochDayOrNull()
        if ((currentControls.startDate.isNotBlank() && start == null) ||
            (currentControls.endDate.isNotBlank() && end == null) ||
            (start != null && end != null && start > end)
        ) {
            writeState.value = writeState.value.copy(error = CsvExportError.INVALID_DATE_RANGE, exportedFileName = null)
            return null
        }
        val selected = if (currentControls.includeAllVehicles) {
            uiState.value.vehicles.mapTo(mutableSetOf()) { it.id }
        } else {
            currentControls.selectedVehicleIds
        }
        if (selected.isEmpty()) {
            writeState.value = writeState.value.copy(error = CsvExportError.NO_VEHICLE_SELECTED, exportedFileName = null)
            return null
        }
        writeState.value = writeState.value.copy(error = null, exportedFileName = null)
        return CsvExportRequest(selected, start, end)
    }

    fun export(request: CsvExportRequest, destinationUriString: String) {
        if (writeState.value.isExporting) return
        viewModelScope.launch {
            writeState.value = CsvExportWriteState(isExporting = true)
            runCatching { csvExportRepository.export(request, destinationUriString) }
                .onSuccess { result -> writeState.value = CsvExportWriteState(exportedFileName = result.fileName) }
                .onFailure { writeState.value = CsvExportWriteState(error = CsvExportError.WRITE_FAILED) }
        }
    }

    companion object {
        fun factory(
            vehicleRepository: VehicleRepository,
            csvExportRepository: CsvExportRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CsvExportViewModel(vehicleRepository, csvExportRepository) }
        }
    }
}

private data class CsvExportControls(
    val includeAllVehicles: Boolean = true,
    val selectedVehicleIds: Set<Long> = emptySet(),
    val startDate: String = "",
    val endDate: String = "",
)

private data class CsvExportWriteState(
    val isExporting: Boolean = false,
    val error: CsvExportError? = null,
    val exportedFileName: String? = null,
)

private fun Vehicle.toCsvExportOption() = CsvExportVehicleOption(id, name, isArchived)

private fun String.toEpochDayOrNull(): Long? =
    trim().takeIf(String::isNotEmpty)?.let { value -> runCatching { LocalDate.parse(value).toEpochDay() }.getOrNull() }

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
