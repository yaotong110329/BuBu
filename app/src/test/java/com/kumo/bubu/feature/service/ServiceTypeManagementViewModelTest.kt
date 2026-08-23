package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.ServiceType
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
}
