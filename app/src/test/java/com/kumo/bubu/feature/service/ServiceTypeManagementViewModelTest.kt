package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceTypeManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun deletingCustomTypeDelegatesToRepository() = runTest {
        val repository = FakeServiceRepository()
        val viewModel = ServiceTypeManagementViewModel(repository)

        viewModel.onEvent(ServiceTypeManagementEvent.DeleteCustomType(8L))
        advanceUntilIdle()

        assertEquals(listOf(8L), repository.deletedTypeIds)
    }

    @Test
    fun reorderingActiveTypesPersistsTheDroppedOrder() = runTest {
        val repository = FakeServiceRepository().apply {
            types.value = listOf(
                serviceType(id = 1L, name = "機油", sortOrder = 0),
                serviceType(id = 2L, name = "機油濾芯", sortOrder = 1),
                serviceType(id = 3L, name = "輪胎", sortOrder = 2),
            )
        }
        val viewModel = ServiceTypeManagementViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(ServiceTypeManagementEvent.Reorder(listOf(3L, 1L, 2L)))
        advanceUntilIdle()

        assertEquals(listOf(listOf(3L, 1L, 2L)), repository.reorderedTypeIds)
    }

    @Test
    fun crossingAnotherItemMovesTheDraggedItemImmediately() {
        val initialOrder = listOf(1L, 2L, 3L)

        val updatedOrder = initialOrder.moveServiceTypeTo(draggedId = 1L, targetId = 3L)

        assertEquals(listOf(2L, 3L, 1L), updatedOrder)
    }

    private fun serviceType(id: Long, name: String, sortOrder: Int) = ServiceType(
        id = id,
        publicId = "type-$id",
        name = name,
        vehicleType = VehicleType.CAR,
        isBuiltIn = true,
        isArchived = false,
        sortOrder = sortOrder,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
