package com.kumo.bubu.feature.fuel

import androidx.lifecycle.SavedStateHandle
import com.kumo.bubu.data.repository.PrivateFormDraftRepository
import org.junit.rules.TemporaryFolder
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.FuelRecord
import com.kumo.bubu.domain.model.FuelPriceQuote
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
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

    @get:Rule
    val draftFiles = TemporaryFolder()

    @Test
    fun normalRecentEconomySavesWithoutAnOldHighEconomyWarningOrAutomaticExclusion() = runTest {
        val history = historyWithEightOldHighAndEightRecentNormalSegments()
        val repository = FakeFuelRepository().apply { records.value = history }
        val form = FuelFormViewModel(repository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())),
            savedStateHandle = SavedStateHandle())
        advanceUntilIdle()
        form.onEvent(FuelFormEvent.DateChanged(java.time.LocalDate.ofEpochDay(20_017).toString()))
        form.onEvent(FuelFormEvent.TimeChanged(""))
        form.onEvent(FuelFormEvent.OdometerChanged("3300"))
        form.onEvent(FuelFormEvent.VolumeChanged("10"))
        form.onEvent(FuelFormEvent.TotalCostChanged("320"))
        form.onEvent(FuelFormEvent.FullTankChanged(true))
        form.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()

        assertEquals(null, form.uiState.value.fuelEconomyOutlier)
        val saved = repository.createdInputs.single()
        assertEquals(3_300L, saved.odometerKm)
        assertEquals(10_000L, saved.fuelVolumeMl)
        assertEquals(320L, saved.totalCostTwd)
        assertEquals(true, saved.isFullTank)
        assertEquals(FuelEconomyStatisticsStatus.UNREVIEWED, saved.fuelEconomyStatisticsStatus)
        assertTrue(repository.fuelEconomyStatisticsUpdates.isEmpty())
        assertEquals(history, repository.records.value)
    }

    @Test
    fun unusualRecentEconomyWaitsForExplicitConfirmationAndKeepsTheEnteredValues() = runTest {
        val history = historyWithEightOldHighAndEightRecentNormalSegments()
        val repository = FakeFuelRepository().apply { records.value = history }
        val form = FuelFormViewModel(repository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())),
            savedStateHandle = SavedStateHandle())
        advanceUntilIdle()
        form.onEvent(FuelFormEvent.DateChanged(java.time.LocalDate.ofEpochDay(20_017).toString()))
        form.onEvent(FuelFormEvent.TimeChanged(""))
        form.onEvent(FuelFormEvent.OdometerChanged("3500"))
        form.onEvent(FuelFormEvent.VolumeChanged("10"))
        form.onEvent(FuelFormEvent.TotalCostChanged("320"))
        form.onEvent(FuelFormEvent.FullTankChanged(true))
        form.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()

        assertEquals(com.kumo.bubu.domain.model.FuelEconomyOutlier(30_000, 10_000), form.uiState.value.fuelEconomyOutlier)
        assertTrue(repository.createdInputs.isEmpty())
        assertTrue(repository.fuelEconomyStatisticsUpdates.isEmpty())
        form.onEvent(FuelFormEvent.ConfirmFuelEconomyOutlier)
        advanceUntilIdle()
        val saved = repository.createdInputs.single()
        assertEquals(3_500L, saved.odometerKm)
        assertEquals(10_000L, saved.fuelVolumeMl)
        assertEquals(320L, saved.totalCostTwd)
        assertEquals(true, saved.isFullTank)
        assertEquals(FuelEconomyStatisticsStatus.INCLUDED, saved.fuelEconomyStatisticsStatus)
        assertEquals(history, repository.records.value)
    }

    @Test
    fun committedFuelCreationFinishesEvenWhenTheFollowingPreferenceWriteFails() = runTest {
        val repository = FakeFuelRepository().apply { failureAfterCreation = java.io.IOException("Preference write failed after commit") }
        val handle = SavedStateHandle()
        val form = FuelFormViewModel(repository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = handle)
        advanceUntilIdle()
        form.onEvent(FuelFormEvent.OdometerChanged("1234"))
        form.onEvent(FuelFormEvent.VolumeChanged("5"))
        form.onEvent(FuelFormEvent.TotalCostChanged("135"))
        form.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(false, form.uiState.value.saveFailed)
        assertEquals(emptySet<String>(), handle.keys())
        form.onEvent(FuelFormEvent.TotalCostChanged("150"))
        form.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(135L, repository.createdInputs.single().totalCostTwd)
    }

    @Test
    fun backgroundSnapshotBeforeCommitDoesNotCreateTheFuelRecordAgain() = runTest {
        val repository = FakeFuelRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.OdometerChanged("1234"))
        original.onEvent(FuelFormEvent.TotalCostChanged("135"))
        original.onEvent(FuelFormEvent.VolumeChanged("5"))
        val backgroundSnapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        original.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(backgroundSnapshot))
        advanceUntilIdle()
        restored.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
    }

    @Test
    fun longFuelNoteSurvivesRecreationWithABoundedBundleAndNoFormalRecord() = runTest {
        val repository = FakeFuelRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = handle, draftRepository = drafts)
        advanceUntilIdle()
        val note = "完整加油長備註".repeat(120_000)
        original.onEvent(FuelFormEvent.NoteChanged(note))
        advanceUntilIdle()
        val bundledBytes = handle.keys().sumOf { (handle.get<Any?>(it) as? String)?.toByteArray(Charsets.UTF_8)?.size ?: 0 }
        assertTrue("Form bundle must stay within 32 KiB", bundledBytes <= 32 * 1024)
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), draftRepository = drafts)
        advanceUntilIdle()
        assertEquals(note, restored.uiState.value.note)
        assertEquals(0, repository.createdInputs.size)
        restored.discardDraft()
        advanceUntilIdle()
        assertEquals(0, draftFiles.root.listFiles()!!.size)
    }

    @Test
    fun restoredFuelDraftKeepsRawInputsAndCalculationSourceOrder() = runTest {
        val repository = FakeFuelRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.OdometerChanged("1234"))
        original.onEvent(FuelFormEvent.TotalCostChanged("135"))
        original.onEvent(FuelFormEvent.VolumeChanged("4.270"))
        original.onEvent(FuelFormEvent.TimeChanged(""))
        original.onEvent(FuelFormEvent.NoteChanged("未知時間，未儲存"))
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("1234", restored.uiState.value.odometerKm)
        assertEquals("135", restored.uiState.value.totalCostTwd)
        assertEquals("4.270", restored.uiState.value.volumeLiters)
        assertEquals("", restored.uiState.value.time)
        assertEquals("未知時間，未儲存", restored.uiState.value.note)
        assertEquals(0, repository.createdInputs.size)
        restored.onEvent(FuelFormEvent.VolumeChanged("5"))
        assertEquals("135", restored.uiState.value.totalCostTwd)
        assertEquals("27", restored.uiState.value.pricePerLiter)
        restored.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(5_000L, repository.createdInputs.single().fuelVolumeMl)
    }

    @Test
    fun restoredDraftCanChangeVehicleAndKeepTheNewVehicleAcrossGarageUpdates() = runTest {
        val repository = FakeFuelRepository()
        val second = vehicle().copy(id = 4, publicId = "second-car", name = "另一台車")
        val vehicles = FakeVehicleRepository(listOf(vehicle(), second))
        val handle = SavedStateHandle()
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.OdometerChanged("1234"))
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        restored.onEvent(FuelFormEvent.VehicleChanged(4))
        advanceUntilIdle()
        vehicles.garage.value = vehicles.garage.value.copy(currentVehiclePublicId = "second-car")
        advanceUntilIdle()
        assertEquals(4L, restored.uiState.value.selectedVehicleId)
        assertEquals(true, restored.uiState.value.canSave)
        assertEquals("1234", restored.uiState.value.odometerKm)
    }

    @Test
    fun restoredFuelEditKeepsChangedValuesAndUpdatesTheOriginalRecord() = runTest {
        val repository = FakeFuelRepository().apply { records.value = listOf(record()) }
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, fuelRecordId = 8, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.NoteChanged("修改尚未儲存"))
        original.onEvent(FuelFormEvent.TotalCostChanged("150"))
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, fuelRecordId = 8,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("150", restored.uiState.value.totalCostTwd)
        assertEquals("修改尚未儲存", restored.uiState.value.note)
        restored.onEvent(FuelFormEvent.Save)
        restored.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(8L, repository.updatedInputs.single().first)
        assertEquals(150L, repository.updatedInputs.single().second.totalCostTwd)
        assertEquals(0, repository.createdInputs.size)
    }

    @Test
    fun missingFuelSourceIsBlockedAndDoesNotBecomeANewRecord() = runTest {
        val repository = FakeFuelRepository().apply { records.value = listOf(record()) }
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, fuelRecordId = 8, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.NoteChanged("尚未儲存"))
        repository.records.value = emptyList()
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, fuelRecordId = 8,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(true, restored.uiState.value.loadFailed)
        assertEquals(false, restored.uiState.value.canSave)
        restored.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(0, repository.updatedInputs.size)
        assertEquals(0, repository.createdInputs.size)
    }

    @Test
    fun cancelAndSaveClearFuelDraftAndDoNotRepeatTheSave() = runTest {
        val repository = FakeFuelRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val cancelled = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        cancelled.onEvent(FuelFormEvent.OdometerChanged("1234"))
        cancelled.discardDraft()
        val saved = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("", saved.uiState.value.odometerKm)
        val savedHandle = SavedStateHandle()
        val form = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = savedHandle)
        advanceUntilIdle()
        form.onEvent(FuelFormEvent.OdometerChanged("1234"))
        form.onEvent(FuelFormEvent.VolumeChanged("5"))
        form.onEvent(FuelFormEvent.TotalCostChanged("150"))
        form.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        form.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
        val reopened = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(savedHandle.keys().associateWith { savedHandle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("", reopened.uiState.value.odometerKm)
    }

    @Test
    fun validFuelRecordIsCreatedOnceWhenSaveIsTappedTwice() = runTest {
        val fuelRepository = FakeFuelRepository()
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
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
    fun unrepresentablePriceIsShownAsAnErrorAndCannotBeSaved() = runTest {
        val fuelRepository = FakeFuelRepository()
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
        advanceUntilIdle()

        viewModel.onEvent(FuelFormEvent.OdometerChanged("1234"))
        viewModel.onEvent(FuelFormEvent.VolumeChanged("0.001"))
        viewModel.onEvent(FuelFormEvent.TotalCostChanged(Long.MAX_VALUE.toString()))

        val state = viewModel.uiState.value
        assertEquals("0.001", state.volumeLiters)
        assertEquals(Long.MAX_VALUE.toString(), state.totalCostTwd)
        assertEquals("", state.pricePerLiter)
        assertTrue(state.validate().errors.containsKey(FuelFormField.PRICE))

        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertTrue(fuelRepository.createdInputs.isEmpty())
    }

    @Test
    fun unrepresentablePriceAfterReversedEntryOrderRemainsBlockedUntilCorrected() = runTest {
        val fuelRepository = FakeFuelRepository()
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
        advanceUntilIdle()

        viewModel.onEvent(FuelFormEvent.OdometerChanged("1234"))
        viewModel.onEvent(FuelFormEvent.TotalCostChanged(Long.MAX_VALUE.toString()))
        viewModel.onEvent(FuelFormEvent.VolumeChanged("0.001"))
        viewModel.onEvent(FuelFormEvent.NoteChanged("保留我的輸入"))
        assertEquals("0.001", viewModel.uiState.value.volumeLiters)
        assertEquals(Long.MAX_VALUE.toString(), viewModel.uiState.value.totalCostTwd)
        assertEquals("", viewModel.uiState.value.pricePerLiter)
        assertEquals("保留我的輸入", viewModel.uiState.value.note)
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(FuelFormError.CALCULATION_OVERFLOW, viewModel.uiState.value.errors[FuelFormField.PRICE])
        assertTrue(fuelRepository.createdInputs.isEmpty())

        viewModel.onEvent(FuelFormEvent.TotalCostChanged("135"))
        assertEquals("135000", viewModel.uiState.value.pricePerLiter)
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(135L, fuelRepository.createdInputs.single().totalCostTwd)
    }

    @Test
    fun unrepresentableVolumeKeepsExplicitPriceAndTotalInEitherOrder() = runTest {
        for (totalFirst in listOf(false, true)) {
            val fuelRepository = FakeFuelRepository()
            val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
            advanceUntilIdle()
            viewModel.onEvent(FuelFormEvent.OdometerChanged("1234"))
            if (totalFirst) {
                viewModel.onEvent(FuelFormEvent.TotalCostChanged(Long.MAX_VALUE.toString()))
                viewModel.onEvent(FuelFormEvent.PriceChanged("0.001"))
            } else {
                viewModel.onEvent(FuelFormEvent.PriceChanged("0.001"))
                viewModel.onEvent(FuelFormEvent.TotalCostChanged(Long.MAX_VALUE.toString()))
            }

            assertEquals("0.001", viewModel.uiState.value.pricePerLiter)
            assertEquals(Long.MAX_VALUE.toString(), viewModel.uiState.value.totalCostTwd)
            assertEquals("", viewModel.uiState.value.volumeLiters)
            assertTrue(viewModel.uiState.value.validate().errors.containsKey(FuelFormField.VOLUME))
            viewModel.onEvent(FuelFormEvent.Save)
            advanceUntilIdle()
            assertTrue(fuelRepository.createdInputs.isEmpty())
        }
    }

    @Test
    fun unrepresentableTotalKeepsExplicitVolumeAndPriceAndCanBeCorrected() = runTest {
        val fuelRepository = FakeFuelRepository()
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.OdometerChanged("1234"))
        viewModel.onEvent(FuelFormEvent.VolumeChanged("1000.001"))
        viewModel.onEvent(FuelFormEvent.PriceChanged("9223372036854775.807"))

        assertEquals("1000.001", viewModel.uiState.value.volumeLiters)
        assertEquals("9223372036854775.807", viewModel.uiState.value.pricePerLiter)
        assertEquals("", viewModel.uiState.value.totalCostTwd)
        assertTrue(viewModel.uiState.value.validate().errors.containsKey(FuelFormField.TOTAL_COST))
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertTrue(fuelRepository.createdInputs.isEmpty())

        viewModel.onEvent(FuelFormEvent.VolumeChanged("4.270"))
        viewModel.onEvent(FuelFormEvent.PriceChanged("31.7"))
        assertEquals("135", viewModel.uiState.value.totalCostTwd)
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals(135L, fuelRepository.createdInputs.single().totalCostTwd)
    }

    @Test
    fun lastFullTankSettingIsUsedForTheSelectedVehicle() = runTest {
        val fuelRepository = FakeFuelRepository().apply { fullTankSettings["vehicle-public-id"] = true }
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())

        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isFullTank)
    }

    @Test
    fun existingFuelRecordLoadsAndUsesUpdatePath() = runTest {
        val fuelRepository = FakeFuelRepository().apply { records.value = listOf(record()) }
        val priceRepository = FakeFuelPriceRepository()
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())), fuelRecordId = 8, savedStateHandle = androidx.lifecycle.SavedStateHandle())
        advanceUntilIdle()
        viewModel.onEvent(FuelFormEvent.TotalCostChanged("150"))
        viewModel.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()

        assertEquals(8L, fuelRepository.updatedInputs.single().first)
        assertEquals(150L, fuelRepository.updatedInputs.single().second.totalCostTwd)
        assertTrue(priceRepository.requestedProducts.isEmpty())
    }

    @Test
    fun restoredFuelDraftRetainsTheTypedOdometerReasonAndRevalidatesBeforeSaving() = runTest {
        val repository = FakeFuelRepository().apply {
            odometerNeighbors = FuelOdometerNeighbors(previous = record(), next = null)
        }
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.OdometerChanged("1000"))
        original.onEvent(FuelFormEvent.VolumeChanged("4.270"))
        original.onEvent(FuelFormEvent.TotalCostChanged("135"))
        original.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        original.onEvent(FuelFormEvent.OdometerOrderReasonChanged("補登舊里程"))
        val restored = FuelFormViewModel(repository, FakeFuelPriceRepository(), vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("補登舊里程", restored.uiState.value.odometerOrderReason)
        assertEquals(null, restored.uiState.value.pendingSaveInput)
        restored.onEvent(FuelFormEvent.ConfirmOdometerOrder)
        advanceUntilIdle()
        assertEquals(0, repository.createdInputs.size)
        restored.onEvent(FuelFormEvent.Save)
        advanceUntilIdle()
        assertEquals("補登舊里程", restored.uiState.value.odometerOrderReason)
        assertEquals(record(), restored.uiState.value.odometerOrderWarning?.previous)
        restored.onEvent(FuelFormEvent.ConfirmOdometerOrder)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
    }

    @Test
    fun outOfOrderOdometerRequiresReasonBeforeSaving() = runTest {
        val fuelRepository = FakeFuelRepository().apply {
            odometerNeighbors = FuelOdometerNeighbors(previous = record(), next = null)
        }
        val viewModel = FuelFormViewModel(fuelRepository, FakeFuelPriceRepository(), FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
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
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())

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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
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
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
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
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
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
        val viewModel = FuelFormViewModel(fuelRepository, priceRepository, FakeVehicleRepository(listOf(vehicle())), savedStateHandle = androidx.lifecycle.SavedStateHandle())
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

    private fun historyWithEightOldHighAndEightRecentNormalSegments() = (0..16).map { index ->
        record().copy(id = index + 1L, publicId = "history-$index", dateEpochDay = 20_000L + index,
            odometerKm = if (index <= 8) index * 300L else 2_400L + (index - 8) * 100L,
            fuelVolumeMl = 10_000, fuelEconomyStatisticsStatus = FuelEconomyStatisticsStatus.INCLUDED)
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
