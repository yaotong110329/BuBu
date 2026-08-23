package com.kumo.bubu.feature.fuel

import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.FuelPriceQuote
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.repository.FuelOdometerNeighbors
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FuelFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun validFuelRecordIsCreatedOnceWhenSaveIsTappedTwice() = runTest {
        val fuelRepository = FakeFuelRepository()
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())))
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.OdometerChanged("1234"))
        viewModel.onEvent(FuelFormEvent.VolumeChanged("4.270"))
        viewModel.onEvent(FuelFormEvent.TotalCostChanged("135"))

        viewModel.onEvent(FuelFormEvent.Save)
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()

        assertEquals(1, fuelRepository.createdInputs.size)
        assertEquals(4_270L, fuelRepository.createdInputs.single().fuelVolumeMl)
    }

    @Test
    fun lastFullTankSettingIsUsedForTheSelectedVehicle() = runTest {
        val fuelRepository = FakeFuelRepository().apply { fullTankSettings["vehicle-public-id"] = true }
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())))

        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isFullTank)
    }

    @Test
    fun existingFuelRecordLoadsAndUsesUpdatePath() = runTest {
        val fuelRepository = FakeFuelRepository().apply { records.value = listOf(record()) }
        val priceRepository = FakeFuelPriceRepository()
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())), fuelRecordId = 8)
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.TotalCostChanged("150"))
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()

        assertEquals(8L, fuelRepository.updatedInputs.single().first)
        assertEquals(150L, fuelRepository.updatedInputs.single().second.totalCostTwd)
        assertTrue(priceRepository.requestedProducts.isEmpty())
    }

    @Test
    fun outOfOrderOdometerRequiresReasonBeforeSaving() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            odometerNeighbors = FuelOdometerNeighbors(previous = record(), next = null)
        }
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())))
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.OdometerChanged("1000"))
        viewModel.onEvent(FuelFormEvent.VolumeChanged("4.270"))
        viewModel.onEvent(FuelFormEvent.TotalCostChanged("135"))

        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()

        assertEquals(record(), viewModel.uiState.value.odometerOrderWarning?.previous)
        assertTrue(fuelRepository.createdInputs.isEmpty())

        viewModel.onEvent(FuelFormEvent.ConfirmOdometerOrder)
        assertTrue(viewModel.uiState.value.odometerOrderReasonRequired)

        viewModel.onEvent(FuelFormEvent.OdometerOrderReasonChanged("補登舊里程"))
        viewModel.onEvent(FuelFormEvent.ConfirmOdometerOrder)
        viewModel.onEvent(FuelFormEvent.ConfirmOdometerOrder)
        advanceUntilIdle()

        assertEquals(1, fuelRepository.createdInputs.size)
    }

    @Test
    fun newFuelRecordUsesCpcManualPriceForLastSelectedProduct() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelProducts["vehicle-public-id"] = FuelProduct.GASOLINE_95
        }
        val priceRepository = FakeFuelPriceRepository().apply {
            quote = FuelPriceQuote(
                product = FuelProduct.GASOLINE_95,
                pricePerLiterMilli = 29_100,
                effectiveDateEpochDay = 20_000,
            )
        }
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())))

        advanceUntilIdle()

        assertEquals(FuelProduct.GASOLINE_95, viewModel.uiState.value.fuelProduct)
        assertEquals("29.1", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.CPC_MANUAL, viewModel.uiState.value.priceSource)
    }

    @Test
    fun selectingGasolineImmediatelyLoadsItsCpcPrice() = runTest {
        val priceRepository = FakeFuelPriceRepository().apply {
            quote = FuelPriceQuote(
                product = FuelProduct.GASOLINE_92,
                pricePerLiterMilli = 30_500,
                effectiveDateEpochDay = 20_000,
            )
        }
        val viewModel = FuelFormViewModel(
            FakeFuelRepository(),
            priceRepository,
            FakeVehicleRepository(listOf(vehicle())),
        )
        advanceUntilIdle()

        viewModel.onEvent(FuelFormEvent.FuelProductChanged(FuelProduct.GASOLINE_92))
        advanceUntilIdle()

        assertEquals(listOf(FuelProduct.GASOLINE_92), priceRepository.requestedProducts)
        assertEquals("30.5", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.CPC_MANUAL, viewModel.uiState.value.priceSource)
    }

    @Test
    fun switchingToSelfServiceAppliesCpcDiscountAndSwitchingBackRestoresListPrice() = runTest {
        val priceRepository = FakeFuelPriceRepository().apply {
            quote = FuelPriceQuote(FuelProduct.GASOLINE_95, 28_900, 20_000)
        }
        val viewModel = FuelFormViewModel(
            FakeFuelRepository(), priceRepository, FakeVehicleRepository(listOf(vehicle())),
        )
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.FuelProductChanged(FuelProduct.GASOLINE_95))
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.FuelingModeChanged(FuelingMode.SELF_SERVICE))
        advanceUntilIdle()
        assertEquals("28.1", viewModel.uiState.value.pricePerLiter)

        viewModel.onEvent(FuelFormEvent.FuelingModeChanged(FuelingMode.FULL_SERVICE))
        advanceUntilIdle()
        assertEquals("28.9", viewModel.uiState.value.pricePerLiter)
    }

    @Test
    fun newFuelRecordLoadsVehicleSpecificFuelingModePreference() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelingModes["vehicle-public-id"] = FuelingMode.SELF_SERVICE
        }
        val viewModel = FuelFormViewModel(
            fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())),
        )
        advanceUntilIdle()
        assertEquals(FuelingMode.SELF_SERVICE, viewModel.uiState.value.fuelingMode)
    }

    @Test
    fun unavailableCpcPriceUsesLastSameProductPrice() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelProducts["vehicle-public-id"] = FuelProduct.GASOLINE_92
            lastPriceForProduct = 28_700
        }
        val viewModel = FuelFormViewModel(
            fuelRepository,
            FakeFuelPriceRepository(),
            FakeVehicleRepository(listOf(vehicle())),
        )

        advanceUntilIdle()

        assertEquals("28.7", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.LAST_RECORD, viewModel.uiState.value.priceSource)
    }

    @Test
    fun failedCpcRequestUsesLastPriceButKeepsTheFailureVisible() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelProducts["vehicle-public-id"] = FuelProduct.GASOLINE_95
            lastPriceForProduct = 28_700
        }
        val priceRepository = FakeFuelPriceRepository().apply {
            failure = IllegalStateException("network unavailable")
        }

        val viewModel = FuelFormViewModel(
            fuelRepository,
            priceRepository,
            FakeVehicleRepository(listOf(vehicle())),
        )
        advanceUntilIdle()

        assertEquals("28.7", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.CPC_FETCH_FAILED_LAST_RECORD, viewModel.uiState.value.priceSource)
    }

    @Test
    fun manualPriceIsNotOverwrittenWhenTheCpcRequestFinishesLater() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelProducts["vehicle-public-id"] = FuelProduct.GASOLINE_95
        }
        val delayedQuote = CompletableDeferred<FuelPriceQuote?>()
        val priceRepository = FakeFuelPriceRepository().apply { pendingQuote = delayedQuote }
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())))
        runCurrent()

        viewModel.onEvent(FuelFormEvent.PriceChanged("30"))
        delayedQuote.complete(
            FuelPriceQuote(FuelProduct.GASOLINE_95, pricePerLiterMilli = 29_100, effectiveDateEpochDay = 20_000),
        )
        advanceUntilIdle()

        assertEquals("30", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.MANUAL, viewModel.uiState.value.priceSource)
    }

    @Test
    fun changingDateDoesNotOverwriteAManualPrice() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelProducts["vehicle-public-id"] = FuelProduct.GASOLINE_95
        }
        val priceRepository = FakeFuelPriceRepository().apply {
            quote = FuelPriceQuote(FuelProduct.GASOLINE_95, 29_100, 20_000)
        }
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())))
        advanceUntilIdle()

        viewModel.onEvent(FuelFormEvent.PriceChanged("30"))
        viewModel.onEvent(FuelFormEvent.DateChanged("2026-08-01"))
        advanceUntilIdle()

        assertEquals("30", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.MANUAL, viewModel.uiState.value.priceSource)
        assertEquals(1, priceRepository.requestedProducts.size)
    }

    @Test
    fun delayedCpcPriceDoesNotOverwriteUserVolumeAndTotalCalculation() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            lastFuelProducts["vehicle-public-id"] = FuelProduct.GASOLINE_95
        }
        val delayedQuote = CompletableDeferred<FuelPriceQuote?>()
        val priceRepository = FakeFuelPriceRepository().apply { pendingQuote = delayedQuote }
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())))
        runCurrent()

        viewModel.onEvent(FuelFormEvent.VolumeChanged("4"))
        viewModel.onEvent(FuelFormEvent.TotalCostChanged("120"))
        delayedQuote.complete(FuelPriceQuote(FuelProduct.GASOLINE_95, 29_100, 20_000))
        advanceUntilIdle()

        assertEquals("4", viewModel.uiState.value.volumeLiters)
        assertEquals("120", viewModel.uiState.value.totalCostTwd)
        assertEquals("30", viewModel.uiState.value.pricePerLiter)
        assertEquals(FuelPriceSource.CALCULATED, viewModel.uiState.value.priceSource)
    }

    private fun vehicle() = Vehicle(
        id = 3,
        publicId = "vehicle-public-id",
        name = "家用車",
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
        currentOdometerKm = 100,
        note = null,
        isArchived = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun record() = FuelRecord(
        id = 8,
        publicId = "fuel-public-id",
        vehicleId = 3,
        dateEpochDay = 20_000,
        timeMinuteOfDay = null,
        sequenceInDay = 0,
        odometerKm = 1_234,
        fuelVolumeMl = 4_270,
        pricePerLiterMilli = 31_700,
        totalCostTwd = 135,
        isFullTank = true,
        fuelProduct = null,
        note = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
