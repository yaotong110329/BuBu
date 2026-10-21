package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.ServiceRecordType
import kotlinx.serialization.Serializable

/** Contains lightweight form controls and private paths; no image bytes or source URIs. */
@Serializable
internal data class ServiceFormDraft(
    val draftId: String,
    val sourceId: Long?,
    val sourcePublicId: String?,
    val selectedVehiclePublicId: String?,
    val preservedPaymentMethod: PaymentMethod?,
    val initialServiceTypeApplied: Boolean,
    val vehicleId: Long?,
    val date: String,
    val time: String,
    val odometer: String,
    val recordType: ServiceRecordType,
    val title: String,
    val items: List<ServiceItemDraft>,
    val attachments: List<ServiceAttachmentDraft>,
    val note: String,
    val isCustomItemDialogVisible: Boolean,
    val isItemPickerVisible: Boolean,
    val editingItemKey: Long?,
    val itemEditorDraft: ServiceItemDraft?,
    val customItemName: String,
    val saveCustomItemAsType: Boolean,
) {
    fun toUiState(): ServiceFormUiState {
        reserveServiceItemDraftKeys(items.map { it.draftKey } + listOfNotNull(itemEditorDraft?.draftKey))
        return ServiceFormUiState(
            editingId = sourceId,
            vehicleId = vehicleId,
            date = date,
            time = time,
            odometer = odometer,
            recordType = recordType,
            title = title,
            items = items,
            attachments = attachments,
            note = note,
            isCustomItemDialogVisible = isCustomItemDialogVisible,
            isItemPickerVisible = isItemPickerVisible,
            editingItemKey = editingItemKey,
            itemEditorDraft = itemEditorDraft,
            customItemName = customItemName,
            saveCustomItemAsType = saveCustomItemAsType,
        )
    }
}

internal fun ServiceFormUiState.toDraft(
    draftId: String,
    sourcePublicId: String?,
    preservedPaymentMethod: PaymentMethod?,
    initialServiceTypeApplied: Boolean,
) = ServiceFormDraft(
    draftId = draftId,
    sourceId = editingId,
    sourcePublicId = sourcePublicId,
    selectedVehiclePublicId = selectedVehicle?.publicId,
    preservedPaymentMethod = preservedPaymentMethod,
    initialServiceTypeApplied = initialServiceTypeApplied,
    vehicleId = vehicleId,
    date = date,
    time = time,
    odometer = odometer,
    recordType = recordType,
    title = title,
    items = items,
    attachments = attachments,
    note = note,
    isCustomItemDialogVisible = isCustomItemDialogVisible,
    isItemPickerVisible = isItemPickerVisible,
    editingItemKey = editingItemKey,
    itemEditorDraft = itemEditorDraft,
    customItemName = customItemName,
    saveCustomItemAsType = saveCustomItemAsType,
)
