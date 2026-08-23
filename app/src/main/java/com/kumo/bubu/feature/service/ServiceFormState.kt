package com.kumo.bubu.feature.service

import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.ServiceType
import com.kumo.bubu.domain.model.Vehicle
import java.util.concurrent.atomic.AtomicLong

data class ServiceItemDraft(
    val draftKey: Long = nextServiceItemDraftKey(),
    val id: Long? = null,
    val serviceTypeId: Long? = null,
    val name: String = "",
    val amount: String = "",
    // These values are retained only when editing a pre-existing record. The current form
    // deliberately does not expose reminder fields, but saving an edit must not erase them.
    val legacyNextDueOdometerKm: Long? = null,
    val legacyNextDueDateEpochDay: Long? = null,
    val note: String = "",
) {
    val amountTwd: Long?
        get() = amount.trim().toLongOrNull()?.takeIf { it >= 0 }
}

data class ServiceAttachmentDraft(
    val id: Long? = null,
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
    val isStaged: Boolean,
)

data class ServiceFormUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val types: List<ServiceType> = emptyList(),
    val vehicleId: Long? = null,
    val date: String = "",
    val time: String = "",
    val odometer: String = "",
    val recordType: ServiceRecordType = ServiceRecordType.MAINTENANCE,
    val title: String = "",
    val items: List<ServiceItemDraft> = emptyList(),
    val attachments: List<ServiceAttachmentDraft> = emptyList(),
    val note: String = "",
    val isCustomItemDialogVisible: Boolean = false,
    val isItemPickerVisible: Boolean = false,
    val itemFeedback: ServiceItemFeedback? = null,
    val editingItemKey: Long? = null,
    val itemEditorDraft: ServiceItemDraft? = null,
    val customItemName: String = "",
    val saveCustomItemAsType: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isImportingAttachments: Boolean = false,
    val error: ServiceFormError? = null,
    val editingId: Long? = null,
) {
    val selectedVehicle: Vehicle?
        get() = vehicles.firstOrNull { it.id == vehicleId }

    val latestOdometerKm: Long?
        get() = selectedVehicle?.currentOdometerKm

    val calculatedItemsTotalTwd: Long?
        get() {
            var total = 0L
            items.forEach { item ->
                val amount = item.amountTwd ?: return@forEach
                total = runCatching { Math.addExact(total, amount) }.getOrNull() ?: return null
            }
            return total
        }
}

enum class ServiceItemFeedback { ADDED, ALREADY_ADDED }

enum class ServiceFormError {
    VEHICLE_REQUIRED,
    INVALID_DATE,
    FUTURE_DATE,
    INVALID_TIME,
    INVALID_ODOMETER,
    TITLE_REQUIRED,
    ITEM_REQUIRED,
    ITEM_NAME_REQUIRED,
    ITEM_AMOUNT_INVALID,
    AMOUNT_OVERFLOW,
    CUSTOM_TYPE_NAME_REQUIRED,
    TYPE_SAVE_FAILED,
    ATTACHMENT_LIMIT_EXCEEDED,
    ATTACHMENT_UNSUPPORTED,
    ATTACHMENT_TOO_LARGE,
    ATTACHMENT_COPY_FAILED,
    ATTACHMENT_CLEANUP_FAILED,
    VEHICLE_ARCHIVED,
    VEHICLE_NOT_FOUND,
    RECORD_NOT_FOUND,
    RECORD_WRITE_FAILED,
    ITEM_WRITE_FAILED,
    REMINDER_WRITE_FAILED,
    ATTACHMENT_LINK_FAILED,
    ODOMETER_WRITE_FAILED,
    SAVE_FAILED,
}

sealed interface ServiceFormEvent {
    data class VehicleChanged(val value: Long) : ServiceFormEvent
    data class DateChanged(val value: String) : ServiceFormEvent
    data class TimeChanged(val value: String) : ServiceFormEvent
    data class OdometerChanged(val value: String) : ServiceFormEvent
    data class RecordTypeChanged(val value: ServiceRecordType) : ServiceFormEvent
    data class TitleChanged(val value: String) : ServiceFormEvent
    data class NoteChanged(val value: String) : ServiceFormEvent

    data class AddTypeItem(val type: ServiceType) : ServiceFormEvent
    data class EditItem(val draftKey: Long) : ServiceFormEvent
    data class RemoveItem(val draftKey: Long) : ServiceFormEvent
    data class ItemNameChanged(val draftKey: Long, val value: String) : ServiceFormEvent
    data class ItemAmountChanged(val draftKey: Long, val value: String) : ServiceFormEvent
    data class ItemNoteChanged(val draftKey: Long, val value: String) : ServiceFormEvent

    data class CustomItemNameChanged(val value: String) : ServiceFormEvent
    data class SaveCustomItemAsTypeChanged(val value: Boolean) : ServiceFormEvent

    data class AttachmentsSelected(val uriStrings: List<String>) : ServiceFormEvent
    data class RemoveAttachment(val relativePath: String) : ServiceFormEvent

    data object OpenCustomItemDialog : ServiceFormEvent
    data object OpenItemPicker : ServiceFormEvent
    data object CloseItemPicker : ServiceFormEvent
    data object CloseCustomItemDialog : ServiceFormEvent
    data object AddCustomItem : ServiceFormEvent
    data object DismissItemFeedback : ServiceFormEvent
    data object CloseItemEditor : ServiceFormEvent
    data object CompleteItemEditor : ServiceFormEvent
    data object Save : ServiceFormEvent
}

private val serviceItemDraftKey = AtomicLong(0L)

private fun nextServiceItemDraftKey(): Long = serviceItemDraftKey.incrementAndGet()
