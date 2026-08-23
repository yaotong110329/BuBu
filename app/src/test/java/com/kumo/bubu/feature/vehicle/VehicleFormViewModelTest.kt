package com.kumo.bubu.feature.vehicle

import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun validNewVehicleIsCreatedOnceEvenWhenSaveIsTappedTwice() = runTest {
        val repository = FakeVehicleRepository()
        val viewModel = VehicleFormViewModel(repository)
        viewModel.onEvent(VehicleFormEvent.NameChanged("家用車"))

        viewModel.onEvent(VehicleFormEvent.Save)
        viewModel.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()

        assertEquals(1, repository.createdInputs.size)
        assertEquals("家用車", repository.createdInputs.single().name)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun existingVehicleLoadsAndUsesUpdatePath() = runTest {
        val repository = FakeVehicleRepository(listOf(vehicle()))
        val viewModel = VehicleFormViewModel(repository, vehicleId = 7)
        advanceUntilIdle()
        assertEquals("舊名稱", viewModel.uiState.value.name)

        viewModel.onEvent(VehicleFormEvent.NameChanged("新名稱"))
        viewModel.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()

        assertEquals(7L, repository.updatedInputs.single().first)
        assertEquals("新名稱", repository.updatedInputs.single().second.name)
    }

    private fun vehicle() = Vehicle(
        id = 7,
        publicId = "stable-id",
        name = "舊名稱",
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 20_000,
        trackingStartOdometerKm = 10,
        currentOdometerKm = 10,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )
}
