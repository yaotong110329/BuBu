package com.kumo.bubu.feature.vehicle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun vehicleActionsReachRepositoryWithExplicitValues() = runTest {
        val repository = FakeVehicleRepository()
        val viewModel = VehiclesViewModel(repository)

        viewModel.onEvent(VehiclesEvent.SelectCurrent("vehicle-2", 2))
        advanceUntilIdle()
        viewModel.onEvent(VehiclesEvent.Archive(2))
        advanceUntilIdle()
        viewModel.onEvent(VehiclesEvent.Unarchive(2))
        advanceUntilIdle()
        viewModel.onEvent(VehiclesEvent.RequestDelete(2))
        viewModel.onEvent(VehiclesEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(listOf("vehicle-2"), repository.selectedPublicIds)
        assertEquals(listOf(2L to true, 2L to false), repository.archiveChanges)
        assertEquals(listOf(2L), repository.deletedIds)
    }
}
