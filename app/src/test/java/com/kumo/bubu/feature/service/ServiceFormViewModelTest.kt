package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newWorkOrderUsesCurrentLocalDateTimeAndShowsLatestVehicleOdometer() = runTest {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals("2026-08-09", viewModel.uiState.value.date)
        assertEquals("14:35", viewModel.uiState.value.time)
        assertEquals(12_345L, viewModel.uiState.value.latestOdometerKm)
    }

    @Test
    fun selectingCommonTypeOpensEditorAndOnlyCompletionAddsIt() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ServiceFormEvent.AddTypeItem(serviceType()))

        assertEquals(0, viewModel.uiState.value.items.size)
        val draft = requireNotNull(viewModel.uiState.value.itemEditorDraft)
        assertEquals("Engine oil", draft.name)
        viewModel.onEvent(ServiceFormEvent.ItemAmountChanged(draft.draftKey, "450"))
        viewModel.onEvent(ServiceFormEvent.CompleteItemEditor)

        assertEquals("450", viewModel.uiState.value.items.single().amount)
        assertEquals(ServiceItemFeedback.ADDED, viewModel.uiState.value.itemFeedback)
    }

    @Test
    fun selectingTheSameCommonTypeReopensItsEditorWithoutDuplicatingIt() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ServiceFormEvent.AddTypeItem(serviceType()))
        val draft = requireNotNull(viewModel.uiState.value.itemEditorDraft)
        viewModel.onEvent(ServiceFormEvent.ItemAmountChanged(draft.draftKey, "450"))
        viewModel.onEvent(ServiceFormEvent.CompleteItemEditor)
        viewModel.onEvent(ServiceFormEvent.AddTypeItem(serviceType()))

        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(viewModel.uiState.value.items.single(), viewModel.uiState.value.itemEditorDraft)
    }

    private fun viewModel(repository: FakeServiceRepository = FakeServiceRepository()) = ServiceFormViewModel(
        serviceRepository = repository,
        vehicleRepository = FakeVehicleRepository(listOf(vehicle())),
        nowProvider = { LocalDateTime.of(2026, 8, 9, 14, 35) },
    )

    private fun vehicle() = Vehicle(
        id = 3,
        publicId = "vehicle-public-id",
        name = "Test car",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 100,
        currentOdometerKm = 12_345,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun serviceType() = ServiceType(
        id = 8,
        publicId = "builtin-oil",
        name = "Engine oil",
        isBuiltIn = true,
        isArchived = false,
        sortOrder = 0,
        createdAt = 1,
        updatedAt = 1,
    )
}
