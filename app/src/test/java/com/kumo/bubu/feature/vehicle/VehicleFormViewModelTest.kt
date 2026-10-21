package com.kumo.bubu.feature.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import com.kumo.bubu.data.repository.PrivateFormDraftRepository
import org.junit.rules.TemporaryFolder
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val draftFiles = TemporaryFolder()

    @Test
    fun cancellingRestoredDraftCleansItsInterruptedWriteButKeepsAnotherDraft() = runTest {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = VehicleFormViewModel(repository, savedStateHandle = handle, draftRepository = drafts)
        original.onEvent(VehicleFormEvent.NoteChanged("完整草稿".repeat(20_000)))
        advanceUntilIdle()
        val published = draftFiles.root.listFiles()!!.single()
        val interrupted = java.io.File(draftFiles.root, "${published.nameWithoutExtension}-interrupted.part")
        interrupted.writeText("unfinished write")
        val otherDraft = java.io.File(draftFiles.root, "af7aa230-8700-47e0-98c7-5e878b111694-interrupted.part")
        otherDraft.writeText("another draft's write")
        original.viewModelScope.cancel()
        val restored = VehicleFormViewModel(repository, savedStateHandle = restoredHandle(handle), draftRepository = drafts)
        advanceUntilIdle()
        restored.discardDraft()
        advanceUntilIdle()
        assertFalse("Cancelled draft must not leave its interrupted private write", interrupted.exists())
        assertFalse(published.exists())
        assertEquals("another draft's write", otherDraft.readText())
        assertEquals(0, repository.createdInputs.size)
    }

    @Test
    fun firstInterruptedPrivateWriteIsReportedWithoutCreatingAnEmptyVehicle() = runTest {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = VehicleFormViewModel(repository, savedStateHandle = handle, draftRepository = drafts)
        original.onEvent(VehicleFormEvent.NoteChanged("尚未完成保存".repeat(20_000)))
        assertEquals(true, original.uiState.value.isDraftSaving)
        val pendingSnapshot = restoredHandle(handle)
        original.viewModelScope.cancel()
        val restored = VehicleFormViewModel(repository, savedStateHandle = pendingSnapshot, draftRepository = drafts)
        advanceUntilIdle()
        assertEquals(true, restored.uiState.value.draftRestoreFailed)
        restored.onEvent(VehicleFormEvent.NameChanged("不可建立空白替代紀錄"))
        restored.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(0, repository.createdInputs.size)
        restored.discardDraft()
        advanceUntilIdle()
        assertEquals(emptySet<String>(), pendingSnapshot.keys())
        assertEquals(0, draftFiles.root.listFiles()!!.size)
    }

    @Test
    fun cancelledPendingLongDraftCannotSilentlyRestoreAnOlderPublishedNote() = runTest {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = VehicleFormViewModel(repository, savedStateHandle = handle, draftRepository = drafts)
        original.onEvent(VehicleFormEvent.NoteChanged("舊內容".repeat(20_000)))
        advanceUntilIdle()
        original.onEvent(VehicleFormEvent.NoteChanged("最新內容".repeat(20_000)))
        val pendingSnapshot = restoredHandle(handle)
        original.viewModelScope.cancel()
        val restored = VehicleFormViewModel(repository, savedStateHandle = pendingSnapshot, draftRepository = drafts)
        advanceUntilIdle()
        assertEquals(true, restored.uiState.value.draftRestoreFailed)
        restored.onEvent(VehicleFormEvent.NameChanged("不得覆蓋成新車"))
        restored.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(0, repository.createdInputs.size)
        restored.discardDraft()
        advanceUntilIdle()
        assertEquals(0, draftFiles.root.listFiles()!!.size)
    }

    @Test
    fun longVehicleNoteSurvivesRecreationWithoutPuttingItsContentIntoTheBundle() = runTest {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = VehicleFormViewModel(repository, savedStateHandle = handle, draftRepository = drafts)
        val note = "完整長備註".repeat(180_000)
        original.onEvent(VehicleFormEvent.NoteChanged(note))
        advanceUntilIdle()
        val bundledBytes = handle.keys().sumOf { (handle.get<Any?>(it) as? String)?.toByteArray(Charsets.UTF_8)?.size ?: 0 }
        assertTrue("Form bundle must stay within 32 KiB", bundledBytes <= 32 * 1024)
        val restored = VehicleFormViewModel(repository, savedStateHandle = restoredHandle(handle), draftRepository = drafts)
        advanceUntilIdle()
        assertEquals(note, restored.uiState.value.note)
        assertEquals(0, repository.createdInputs.size)
        restored.discardDraft()
        advanceUntilIdle()
        assertEquals(0, draftFiles.root.listFiles()!!.size)
    }

    @Test
    fun unsavedVehicleInputsSurviveSavedStateRecreationWithoutCreatingAVehicle() = runTest {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val original = VehicleFormViewModel(repository, savedStateHandle = handle)
        original.onEvent(VehicleFormEvent.NameChanged("DRAFT06-CAR"))
        original.onEvent(VehicleFormEvent.BrandChanged("BRAND06"))
        original.onEvent(VehicleFormEvent.ManufactureYearChanged("not-a-year"))
        original.onEvent(VehicleFormEvent.NoteChanged("尚未儲存"))

        val recreated = VehicleFormViewModel(repository, savedStateHandle = restoredHandle(handle))
        advanceUntilIdle()

        assertEquals("DRAFT06-CAR", recreated.uiState.value.name)
        assertEquals("BRAND06", recreated.uiState.value.brand)
        assertEquals("not-a-year", recreated.uiState.value.manufactureYear)
        assertEquals("尚未儲存", recreated.uiState.value.note)
        assertEquals(emptyList<com.kumo.bubu.domain.model.VehicleInput>(), repository.createdInputs)
    }

    @Test
    fun restoredEditKeepsUnsavedInputsAndUpdatesTheOriginalVehicle() = runTest {
        val repository = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = VehicleFormViewModel(repository, vehicleId = 7, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(VehicleFormEvent.NameChanged("我的未儲存修改"))
        original.onEvent(VehicleFormEvent.TrackingStartOdometerChanged("56"))
        val recreated = VehicleFormViewModel(repository, vehicleId = 7, savedStateHandle = restoredHandle(handle))
        advanceUntilIdle()
        assertEquals("我的未儲存修改", recreated.uiState.value.name)
        assertEquals("56", recreated.uiState.value.trackingStartOdometerKm)
        recreated.onEvent(VehicleFormEvent.Save)
        recreated.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(7L, repository.updatedInputs.single().first)
        assertEquals("我的未儲存修改", repository.updatedInputs.single().second.name)
    }

    @Test
    fun replacedSourceIdentityCannotReceiveARestoredVehicleEdit() = runTest {
        val repository = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = VehicleFormViewModel(repository, vehicleId = 7, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(VehicleFormEvent.NameChanged("舊車修改"))
        repository.garage.value = repository.garage.value.copy(vehicles = listOf(vehicle().copy(publicId = "different-car")))
        val restoredState = restoredHandle(handle)
        val recreated = VehicleFormViewModel(repository, vehicleId = 7, savedStateHandle = restoredState)
        advanceUntilIdle()
        assertEquals(true, recreated.uiState.value.loadFailed)
        recreated.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(0, repository.updatedInputs.size)
        assertEquals("舊名稱", VehicleFormViewModel(repository, 7, restoredHandle(restoredState)).let {
            advanceUntilIdle()
            it.uiState.value.name
        })
    }

    @Test
    fun cancellationAndSuccessfulSaveDoNotResurrectVehicleDrafts() = runTest {
        val repository = FakeVehicleRepository()
        val cancelledHandle = SavedStateHandle()
        val cancelled = VehicleFormViewModel(repository, savedStateHandle = cancelledHandle)
        cancelled.onEvent(VehicleFormEvent.NameChanged("不要保存"))
        cancelled.discardDraft()
        assertEquals("", VehicleFormViewModel(repository, savedStateHandle = restoredHandle(cancelledHandle)).uiState.value.name)
        val savedHandle = SavedStateHandle()
        val saved = VehicleFormViewModel(repository, savedStateHandle = savedHandle)
        saved.onEvent(VehicleFormEvent.NameChanged("已儲存車輛"))
        saved.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        saved.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
        assertEquals("", VehicleFormViewModel(repository, savedStateHandle = restoredHandle(savedHandle)).uiState.value.name)
    }

    @Test
    fun committedVehicleCreationFinishesEvenWhenTheFollowingSelectionWriteFails() = runTest {
        val repository = FakeVehicleRepository().apply { failureAfterCreation = java.io.IOException("Selection write failed after commit") }
        val handle = SavedStateHandle()
        val form = VehicleFormViewModel(repository, savedStateHandle = handle)
        form.onEvent(VehicleFormEvent.NameChanged("已提交的車"))
        form.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(false, form.uiState.value.saveFailed)
        assertEquals(emptySet<String>(), handle.keys())
        form.onEvent(VehicleFormEvent.NameChanged("不應再留在新增表單"))
        form.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals("已提交的車", repository.createdInputs.single().name)
    }

    @Test
    fun backgroundSnapshotCapturedBeforeSaveDoesNotCreateTheVehicleAgain() = runTest {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val original = VehicleFormViewModel(repository, savedStateHandle = handle)
        original.onEvent(VehicleFormEvent.NameChanged("背景完成儲存的車"))
        val backgroundSnapshot = restoredHandle(handle)
        original.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        val recreated = VehicleFormViewModel(repository, savedStateHandle = backgroundSnapshot)
        advanceUntilIdle()
        recreated.onEvent(VehicleFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
    }

    private fun restoredHandle(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test
    fun validNewVehicleIsCreatedOnceEvenWhenSaveIsTappedTwice() = runTest {
        val repository = FakeVehicleRepository()
        val viewModel = VehicleFormViewModel(repository, savedStateHandle = androidx.lifecycle.SavedStateHandle())
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
        val viewModel = VehicleFormViewModel(repository, vehicleId = 7, savedStateHandle = androidx.lifecycle.SavedStateHandle())
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
