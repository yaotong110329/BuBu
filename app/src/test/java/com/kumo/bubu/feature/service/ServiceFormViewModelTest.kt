package com.kumo.bubu.feature.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import com.kumo.bubu.data.repository.PrivateFormDraftRepository
import org.junit.rules.TemporaryFolder
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.ServiceRecord
import com.kumo.bubu.domain.model.ServiceRecordDetails
import com.kumo.bubu.domain.model.ServiceItem
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.Vehicle
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.feature.vehicle.FakeVehicleRepository
import com.kumo.bubu.feature.vehicle.MainDispatcherRule
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val draftFiles = TemporaryFolder()

    @Test
    fun attachmentCompletedAfterBackgroundSnapshotKeepsLargeDraftOnProcessRecreation() = runTest {
        val importMayFinish = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = FakeServiceRepository().apply { beforeAttachmentStage = { importMayFinish.await() } }
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = ServiceFormViewModel(repository, vehicles, savedStateHandle = handle, draftRepository = drafts)
        advanceUntilIdle()
        val note = "背景操作前已輸入的長備註".repeat(10_000)
        original.onEvent(ServiceFormEvent.TitleChanged("程序回收仍保留工單"))
        original.onEvent(ServiceFormEvent.NoteChanged(note))
        original.onEvent(ServiceFormEvent.AttachmentsSelected(listOf("content://test/selected-image")))
        advanceUntilIdle()
        assertEquals(true, original.uiState.value.isImportingAttachments)
        assertEquals(false, original.uiState.value.isDraftSaving)
        val backgroundSnapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        importMayFinish.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, original.uiState.value.attachments.size)
        assertEquals(false, original.uiState.value.isDraftSaving)
        val attachmentPath = original.uiState.value.attachments.single().relativePath
        original.viewModelScope.cancel()

        val restored = ServiceFormViewModel(repository, vehicles,
            savedStateHandle = SavedStateHandle(backgroundSnapshot), draftRepository = drafts)
        advanceUntilIdle()
        assertEquals(note, restored.uiState.value.note)
        assertEquals("程序回收仍保留工單", restored.uiState.value.title)
        assertEquals(false, restored.uiState.value.sourceInvalid)
        assertEquals(listOf(attachmentPath), restored.uiState.value.attachments.map { it.relativePath })
        assertEquals(0, repository.createdInputs.size)
        restored.discardUnsavedAttachments {}
        advanceUntilIdle()
        assertEquals(null, repository.readServiceAttachmentBytes(attachmentPath))
        assertEquals(0, draftFiles.root.listFiles()!!.size)
    }

    @Test
    fun sourceInvalidatedDuringAttachmentImportCannotRecreateCancelledOwnership() = runTest {
        val importMayFinish = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = FakeServiceRepository().apply { beforeAttachmentStage = { importMayFinish.await() } }
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val form = ServiceFormViewModel(repository, vehicles, savedStateHandle = androidx.lifecycle.SavedStateHandle())
        advanceUntilIdle()
        form.onEvent(ServiceFormEvent.AttachmentsSelected(listOf("content://test/selected-image")))
        advanceUntilIdle()
        assertEquals(true, form.uiState.value.isImportingAttachments)
        vehicles.garage.value = vehicles.garage.value.copy(vehicles = emptyList())
        advanceUntilIdle()
        assertEquals(true, form.uiState.value.sourceInvalid)
        importMayFinish.complete(Unit)
        advanceUntilIdle()
        assertEquals(emptyList<ServiceAttachmentDraft>(), form.uiState.value.attachments)
        assertEquals(null, repository.readServiceAttachmentBytes("service_attachments/staging/0.jpg"))
        assertEquals(false, form.uiState.value.isImportingAttachments)
        form.onEvent(ServiceFormEvent.Save)
        advanceUntilIdle()
        assertEquals(0, repository.createdInputs.size)
    }

    @Test
    fun backgroundSnapshotBeforeCommitDoesNotCreateTheServiceRecordAgain() = runTest {
        val repository = FakeServiceRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = ServiceFormViewModel(repository, vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(ServiceFormEvent.OdometerChanged("2345"))
        original.onEvent(ServiceFormEvent.TitleChanged("只建立一次工單"))
        original.onEvent(ServiceFormEvent.AddTypeItem(serviceType()))
        val key = requireNotNull(original.uiState.value.itemEditorDraft).draftKey
        original.onEvent(ServiceFormEvent.ItemAmountChanged(key, "450"))
        original.onEvent(ServiceFormEvent.CompleteItemEditor)
        val backgroundSnapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        original.onEvent(ServiceFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
        val restored = ServiceFormViewModel(repository, vehicles, savedStateHandle = SavedStateHandle(backgroundSnapshot))
        advanceUntilIdle()
        restored.onEvent(ServiceFormEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.createdInputs.size)
    }

    @Test
    fun longServiceNotesAndUnfinishedItemSurviveWithABoundedBundle() = runTest {
        val repository = FakeServiceRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val drafts = PrivateFormDraftRepository(draftFiles.root, mainDispatcherRule.dispatcher)
        val original = ServiceFormViewModel(repository, vehicles, savedStateHandle = handle, draftRepository = drafts)
        advanceUntilIdle()
        val note = "完整工單備註".repeat(100_000)
        val itemNote = "未完成項目備註".repeat(80_000)
        original.onEvent(ServiceFormEvent.NoteChanged(note))
        original.onEvent(ServiceFormEvent.AddTypeItem(serviceType()))
        val key = requireNotNull(original.uiState.value.itemEditorDraft).draftKey
        original.onEvent(ServiceFormEvent.ItemNoteChanged(key, itemNote))
        advanceUntilIdle()
        val bundledBytes = handle.keys().sumOf { (handle.get<Any?>(it) as? String)?.toByteArray(Charsets.UTF_8)?.size ?: 0 }
        assertTrue("Form bundle must stay within 32 KiB", bundledBytes <= 32 * 1024)
        val restored = ServiceFormViewModel(repository, vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), draftRepository = drafts)
        advanceUntilIdle()
        assertEquals(note, restored.uiState.value.note)
        assertEquals(itemNote, restored.uiState.value.itemEditorDraft?.note)
        assertEquals(key, restored.uiState.value.itemEditorDraft?.draftKey)
        assertEquals(0, repository.createdInputs.size)
        restored.discardUnsavedAttachments {}
        advanceUntilIdle()
        assertEquals(0, draftFiles.root.listFiles()!!.size)
    }

    @Test
    fun restoredServiceDraftKeepsInputsItemOrderAndUnfinishedEditorWithoutSaving() = runTest {
        val repository = FakeServiceRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = ServiceFormViewModel(repository, vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(ServiceFormEvent.OdometerChanged("2345"))
        original.onEvent(ServiceFormEvent.TitleChanged("未儲存工單"))
        original.onEvent(ServiceFormEvent.TimeChanged(""))
        original.onEvent(ServiceFormEvent.AddTypeItem(serviceType()))
        val first = requireNotNull(original.uiState.value.itemEditorDraft)
        original.onEvent(ServiceFormEvent.ItemAmountChanged(first.draftKey, "450"))
        original.onEvent(ServiceFormEvent.CompleteItemEditor)
        original.onEvent(ServiceFormEvent.AddTypeItem(serviceType().copy(id = 9, publicId = "brakes", name = "Brakes")))
        val second = requireNotNull(original.uiState.value.itemEditorDraft)
        original.onEvent(ServiceFormEvent.ItemAmountChanged(second.draftKey, "600"))
        original.onEvent(ServiceFormEvent.CompleteItemEditor)
        original.onEvent(ServiceFormEvent.EditItem(first.draftKey))
        original.onEvent(ServiceFormEvent.ItemAmountChanged(first.draftKey, "invalid-amount"))

        val recreated = ServiceFormViewModel(repository, vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("2345", recreated.uiState.value.odometer)
        assertEquals("未儲存工單", recreated.uiState.value.title)
        assertEquals("", recreated.uiState.value.time)
        assertEquals(listOf("Engine oil", "Brakes"), recreated.uiState.value.items.map { it.name })
        assertEquals(listOf(first.draftKey, second.draftKey), recreated.uiState.value.items.map { it.draftKey })
        assertEquals("invalid-amount", recreated.uiState.value.itemEditorDraft?.amount)
        assertEquals(0, repository.createdInputs.size)
    }

    @Test
    fun restoredServiceEditKeepsModifiedItemAndLegacyFieldsForTheOriginalRecord() = runTest {
        val repository = FakeServiceRepository()
        repository.details[12] = ServiceRecordDetails(
            ServiceRecord(12, "stable-service", 3, 20_000, null, 0, 1200,
                ServiceRecordType.MAINTENANCE, "原工單", PaymentMethod.CREDIT_CARD, 450, null, 1, 1),
            listOf(ServiceItem(13, "stable-item", 12, 8, 0, "Engine oil", 1000,
                ServiceQuantityUnit.PIECE, 450, 450, 1800, 20_050, "原備註", 1, 1)),
        )
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = ServiceFormViewModel(repository, vehicles, editId = 12, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(ServiceFormEvent.TitleChanged("修改工單"))
        val key = original.uiState.value.items.single().draftKey
        original.onEvent(ServiceFormEvent.EditItem(key))
        original.onEvent(ServiceFormEvent.ItemAmountChanged(key, "600"))
        original.onEvent(ServiceFormEvent.CompleteItemEditor)
        val restored = ServiceFormViewModel(repository, vehicles, editId = 12,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("修改工單", restored.uiState.value.title)
        assertEquals("600", restored.uiState.value.items.single().amount)
        restored.onEvent(ServiceFormEvent.Save)
        restored.onEvent(ServiceFormEvent.Save)
        advanceUntilIdle()
        val (id, input) = repository.updatedInputs.single()
        assertEquals(12L, id)
        assertEquals(PaymentMethod.CREDIT_CARD, input.paymentMethod)
        assertEquals(13L, input.items.single().id)
        assertEquals(1800L, input.items.single().nextDueOdometerKm)
        assertEquals(20_050L, input.items.single().nextDueDateEpochDay)
        assertEquals(600L, input.totalCostTwd)
        assertEquals(0, repository.createdInputs.size)
    }

    @Test
    fun cancellingAnUnattachedServiceDraftDoesNotResurrectInputs() = runTest {
        val repository = FakeServiceRepository()
        val vehicles = FakeVehicleRepository(listOf(vehicle()))
        val handle = SavedStateHandle()
        val original = ServiceFormViewModel(repository, vehicles, savedStateHandle = handle)
        advanceUntilIdle()
        original.onEvent(ServiceFormEvent.TitleChanged("取消工單"))
        var left = false
        original.discardUnsavedAttachments { left = true }
        advanceUntilIdle()
        assertEquals(true, left)
        val reopened = ServiceFormViewModel(repository, vehicles,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("", reopened.uiState.value.title)
        assertEquals(0, repository.createdInputs.size)
    }

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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
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
