package com.kumo.bubu.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.ExpenseRecord
import com.kumo.bubu.domain.model.ExpenseRecordInput
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.repository.ExpenseRepository
import com.kumo.bubu.domain.repository.VehicleRepository
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpenseFormUiState(val vehicles: List<Vehicle> = emptyList(), val vehicleId: Long? = null, val date: String = LocalDate.now().toString(), val time: String = "", val category: ExpenseCategory = ExpenseCategory.OTHER, val total: String = "", val note: String = "", val completeSameCycleReminder: Boolean = false, val isLoading: Boolean = true, val isSaving: Boolean = false, val error: Boolean = false, val editId: Long? = null)
sealed interface ExpenseFormEvent { data class Vehicle(val id: Long) : ExpenseFormEvent; data class Date(val value: String) : ExpenseFormEvent; data class Time(val value: String) : ExpenseFormEvent; data class Category(val value: ExpenseCategory) : ExpenseFormEvent; data class Total(val value: String) : ExpenseFormEvent; data class Note(val value: String) : ExpenseFormEvent; data class CompleteSameCycleReminder(val value: Boolean) : ExpenseFormEvent; data object Save : ExpenseFormEvent }
class ExpenseFormViewModel(private val expenses: ExpenseRepository, private val vehicles: VehicleRepository, private val editId: Long? = null) : ViewModel() {
    private val _state = MutableStateFlow(ExpenseFormUiState(editId = editId)); val state: StateFlow<ExpenseFormUiState> = _state.asStateFlow(); private val saved = Channel<Unit>(Channel.BUFFERED); val savedEffects = saved.receiveAsFlow()
    init { viewModelScope.launch { vehicles.observeGarage().collect { garage -> _state.update { state -> val active = garage.vehicles.filterNot { it.isArchived && it.id != state.vehicleId }; state.copy(vehicles = active, vehicleId = state.vehicleId ?: active.firstOrNull()?.id, isLoading = false) } } }; if (editId != null) viewModelScope.launch { expenses.getExpenseRecord(editId)?.let { record -> _state.update { state -> state.copy(vehicles = (state.vehicles + listOfNotNull(vehicles.getVehicle(record.vehicleId))).distinctBy { it.id }, vehicleId = record.vehicleId, date = LocalDate.ofEpochDay(record.dateEpochDay).toString(), time = record.timeMinuteOfDay?.let { java.time.LocalTime.ofSecondOfDay(it * 60L).toString() }.orEmpty(), category = record.category, total = record.totalCostTwd.toString(), note = record.note.orEmpty()) } } } }
    fun onEvent(event: ExpenseFormEvent) { when (event) { is ExpenseFormEvent.Vehicle -> change { copy(vehicleId = event.id) }; is ExpenseFormEvent.Date -> change { copy(date = event.value) }; is ExpenseFormEvent.Time -> change { copy(time = event.value) }; is ExpenseFormEvent.Category -> change { copy(category = event.value, completeSameCycleReminder = completeSameCycleReminder && event.value.isStatutoryExpense()) }; is ExpenseFormEvent.Total -> change { copy(total = event.value) }; is ExpenseFormEvent.Note -> change { copy(note = event.value) }; is ExpenseFormEvent.CompleteSameCycleReminder -> change { copy(completeSameCycleReminder = event.value) }; ExpenseFormEvent.Save -> save() } }
    private fun change(transform: ExpenseFormUiState.() -> ExpenseFormUiState) = _state.update { it.transform().copy(error = false) }
    private fun save() { if (_state.value.isSaving) return; val state = _state.value; val input = runCatching { ExpenseRecordInput(requireNotNull(state.vehicleId), LocalDate.parse(state.date).toEpochDay(), state.time.takeIf(String::isNotBlank)?.let(java.time.LocalTime::parse)?.toSecondOfDay()?.div(60), state.category, state.total.toLong(), state.note, state.completeSameCycleReminder && state.editId == null) }.getOrElse { _state.update { it.copy(error = true) }; return }; _state.update { it.copy(isSaving = true) }; viewModelScope.launch { runCatching { if (state.editId == null) expenses.createExpenseRecord(input) else expenses.updateExpenseRecord(state.editId, input) }.onSuccess { _state.update { it.copy(isSaving = false) }; saved.send(Unit) }.onFailure { _state.update { it.copy(isSaving = false, error = true) } } } }
    companion object { fun factory(expenses: ExpenseRepository, vehicles: VehicleRepository, editId: Long? = null): ViewModelProvider.Factory = viewModelFactory { initializer { ExpenseFormViewModel(expenses, vehicles, editId) } } }
}

private fun ExpenseCategory.isStatutoryExpense(): Boolean =
    this == ExpenseCategory.LICENSE_TAX || this == ExpenseCategory.ROAD_MAINTENANCE_FEE

data class ExpenseRecordsUiState(val records: List<ExpenseRecord> = emptyList(), val isLoading: Boolean = true, val failed: Boolean = false, val deleteFailed: Boolean = false)
class ExpenseRecordsViewModel(private val expenses: ExpenseRepository) : ViewModel() { private val deletingIds = mutableSetOf<Long>(); private val deleteFailed = MutableStateFlow(false); val state = combine(expenses.observeRecentExpenseRecords(), deleteFailed) { records, deleteError -> ExpenseRecordsUiState(records, false, deleteFailed = deleteError) }.catch { emit(ExpenseRecordsUiState(isLoading = false, failed = true)) }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), ExpenseRecordsUiState()); fun delete(id: Long) { if (deletingIds.add(id)) viewModelScope.launch { try { expenses.deleteExpenseRecord(id); deleteFailed.value = false } catch (_: Throwable) { deleteFailed.value = true } finally { deletingIds.remove(id) } } }; companion object { fun factory(expenses: ExpenseRepository): ViewModelProvider.Factory = viewModelFactory { initializer { ExpenseRecordsViewModel(expenses) } } } }
