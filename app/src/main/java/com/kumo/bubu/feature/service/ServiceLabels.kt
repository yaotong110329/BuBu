package com.kumo.bubu.feature.service

import androidx.annotation.StringRes
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ServiceRecordType

@StringRes
fun ServiceRecordType.labelRes(): Int = when (this) {
    ServiceRecordType.MAINTENANCE -> R.string.service_type_maintenance
    ServiceRecordType.REPAIR -> R.string.service_type_repair
    ServiceRecordType.INSPECTION -> R.string.service_type_inspection
}

@StringRes
fun ServiceQuantityUnit.labelRes(): Int = when (this) {
    ServiceQuantityUnit.PIECE -> R.string.service_unit_piece
    ServiceQuantityUnit.SET -> R.string.service_unit_set
    ServiceQuantityUnit.LITER -> R.string.service_unit_liter
    ServiceQuantityUnit.BOTTLE -> R.string.service_unit_bottle
    ServiceQuantityUnit.STRIP -> R.string.service_unit_strip
    ServiceQuantityUnit.SHEET -> R.string.service_unit_sheet
    ServiceQuantityUnit.HOUR -> R.string.service_unit_hour
    ServiceQuantityUnit.OTHER -> R.string.service_unit_other
}

@StringRes
fun ServiceFormError.messageRes(): Int = when (this) {
    ServiceFormError.VEHICLE_REQUIRED -> R.string.service_error_vehicle_required
    ServiceFormError.INVALID_DATE -> R.string.invalid_date_error
    ServiceFormError.FUTURE_DATE -> R.string.service_error_future_date
    ServiceFormError.INVALID_TIME -> R.string.invalid_time_error
    ServiceFormError.INVALID_ODOMETER -> R.string.non_negative_integer_error
    ServiceFormError.TITLE_REQUIRED -> R.string.service_error_title_required
    ServiceFormError.ITEM_REQUIRED -> R.string.service_error_item_required
    ServiceFormError.ITEM_NAME_REQUIRED -> R.string.service_error_item_name_required
    ServiceFormError.ITEM_AMOUNT_INVALID -> R.string.service_error_item_amount
    ServiceFormError.AMOUNT_OVERFLOW -> R.string.service_error_amount_overflow
    ServiceFormError.CUSTOM_TYPE_NAME_REQUIRED -> R.string.service_error_custom_type_name
    ServiceFormError.TYPE_SAVE_FAILED -> R.string.service_error_type_save
    ServiceFormError.ATTACHMENT_LIMIT_EXCEEDED -> R.string.service_error_attachment_limit
    ServiceFormError.ATTACHMENT_UNSUPPORTED -> R.string.service_error_attachment_unsupported
    ServiceFormError.ATTACHMENT_TOO_LARGE -> R.string.service_error_attachment_size
    ServiceFormError.ATTACHMENT_COPY_FAILED -> R.string.service_error_attachment_copy
    ServiceFormError.ATTACHMENT_CLEANUP_FAILED -> R.string.service_error_attachment_cleanup
    ServiceFormError.VEHICLE_ARCHIVED -> R.string.service_error_vehicle_archived
    ServiceFormError.VEHICLE_NOT_FOUND -> R.string.service_error_vehicle_not_found
    ServiceFormError.RECORD_NOT_FOUND -> R.string.service_error_record_not_found
    ServiceFormError.RECORD_WRITE_FAILED -> R.string.service_error_record_write
    ServiceFormError.ITEM_WRITE_FAILED -> R.string.service_error_item_write
    ServiceFormError.REMINDER_WRITE_FAILED -> R.string.service_error_reminder_write
    ServiceFormError.ATTACHMENT_LINK_FAILED -> R.string.service_error_attachment_link
    ServiceFormError.ODOMETER_WRITE_FAILED -> R.string.service_error_odometer_write
    ServiceFormError.SAVE_FAILED -> R.string.service_save_error
}
