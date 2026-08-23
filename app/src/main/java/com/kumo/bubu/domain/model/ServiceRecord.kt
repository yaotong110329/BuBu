package com.kumo.bubu.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class ServiceRecord(
    val id: Long,
    val publicId: String,
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val recordType: ServiceRecordType,
    val title: String,
    val paymentMethod: PaymentMethod?,
    val totalCostTwd: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ServiceItem(
    val id: Long,
    val publicId: String,
    val serviceRecordId: Long,
    val serviceTypeId: Long?,
    val sequenceInRecord: Int,
    val nameSnapshot: String,
    val quantityMilli: Long,
    val quantityUnit: ServiceQuantityUnit,
    val unitPriceTwd: Long,
    val subtotalTwd: Long,
    val nextDueOdometerKm: Long?,
    val nextDueDateEpochDay: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ServiceAttachment(
    val id: Long,
    val publicId: String,
    val serviceRecordId: Long,
    val sequenceInRecord: Int,
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
    val createdAt: Long,
)

data class ServiceRecordDetails(
    val record: ServiceRecord,
    val items: List<ServiceItem>,
    val attachments: List<ServiceAttachment> = emptyList(),
)

data class ServiceRecordInput(
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val odometerKm: Long,
    val recordType: ServiceRecordType,
    val title: String,
    val paymentMethod: PaymentMethod?,
    val totalCostTwd: Long,
    val note: String? = null,
    val items: List<ServiceItemInput>,
    val attachments: List<ServiceAttachmentInput> = emptyList(),
)

data class ServiceItemInput(
    val nameSnapshot: String,
    val serviceTypeId: Long? = null,
    val quantityMilli: Long,
    val quantityUnit: ServiceQuantityUnit = ServiceQuantityUnit.PIECE,
    val unitPriceTwd: Long,
    val subtotalTwd: Long = calculateServiceSubtotalTwd(quantityMilli, unitPriceTwd)
        ?: throw IllegalArgumentException("Service item subtotal is too large."),
    val nextDueOdometerKm: Long? = null,
    val nextDueDateEpochDay: Long? = null,
    val note: String? = null,
    val id: Long? = null,
)

data class ServiceAttachmentInput(
    val id: Long? = null,
    val relativePath: String,
    val displayName: String,
    val mimeType: String? = null,
    val isStaged: Boolean,
)

data class StagedServiceAttachment(
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
)

enum class ServiceRecordType { MAINTENANCE, REPAIR, INSPECTION }

enum class PaymentMethod { CASH, CREDIT_CARD, MOBILE_PAYMENT, BANK_TRANSFER, OTHER }

enum class ServiceQuantityUnit { PIECE, SET, LITER, BOTTLE, STRIP, SHEET, HOUR, OTHER }

fun parseServiceQuantityMilli(raw: String): Long? {
    val normalized = raw.trim()
    if (normalized.isEmpty()) return null
    return runCatching {
        val decimal = BigDecimal(normalized)
        if (decimal.scale() > SERVICE_QUANTITY_SCALE || decimal <= BigDecimal.ZERO) return null
        decimal.setScale(SERVICE_QUANTITY_SCALE)
            .movePointRight(SERVICE_QUANTITY_SCALE)
            .longValueExact()
    }.getOrNull()
}

fun calculateServiceSubtotalTwd(quantityMilli: Long, unitPriceTwd: Long): Long? {
    if (quantityMilli <= 0 || unitPriceTwd < 0) return null
    return runCatching {
        BigDecimal.valueOf(quantityMilli)
            .multiply(BigDecimal.valueOf(unitPriceTwd))
            .divide(SERVICE_QUANTITY_DIVISOR, 0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()
}

fun ServiceRecordInput.validated(today: LocalDate = LocalDate.now()): ServiceRecordInput {
    require(dateEpochDay <= today.toEpochDay()) { "Service date cannot be in the future." }
    require(timeMinuteOfDay == null || timeMinuteOfDay in 0 until 24 * 60) { "Service time is invalid." }
    require(odometerKm >= 0) { "Service odometer cannot be negative." }
    require(title.isNotBlank()) { "Service title cannot be blank." }
    require(totalCostTwd >= 0) { "Service total cannot be negative." }
    require(items.isNotEmpty()) { "A service record needs at least one item." }
    require(items.mapNotNull { it.id }.distinct().size == items.mapNotNull { it.id }.size) {
        "Service item is duplicated."
    }
    val validItems = items.map(ServiceItemInput::validated)
    val calculatedTotal = validItems.fold(0L) { total, item -> Math.addExact(total, item.subtotalTwd) }
    require(totalCostTwd == calculatedTotal) { "Service total must equal the item total." }
    require(attachments.mapNotNull { it.id }.distinct().size == attachments.mapNotNull { it.id }.size) {
        "Service attachment is duplicated."
    }
    require(attachments.map { it.relativePath }.distinct().size == attachments.size) {
        "Service attachment path is duplicated."
    }
    return copy(
        title = title.trim(),
        note = note?.trim()?.takeIf(String::isNotEmpty),
        items = validItems,
        attachments = attachments.map(ServiceAttachmentInput::validated),
    )
}

fun ServiceItemInput.validated(): ServiceItemInput {
    require(nameSnapshot.isNotBlank()) { "Service item name cannot be blank." }
    require(quantityMilli > 0) { "Service item quantity is invalid." }
    require(unitPriceTwd >= 0) { "Service item price cannot be negative." }
    val calculatedSubtotal = requireNotNull(calculateServiceSubtotalTwd(quantityMilli, unitPriceTwd)) {
        "Service item subtotal is too large."
    }
    require(subtotalTwd == calculatedSubtotal) { "Service item subtotal is invalid." }
    require(nextDueOdometerKm == null || nextDueOdometerKm >= 0) { "Next due odometer is invalid." }
    require(nextDueDateEpochDay == null || nextDueDateEpochDay >= 0) { "Next due date is invalid." }
    return copy(
        nameSnapshot = nameSnapshot.trim(),
        subtotalTwd = calculatedSubtotal,
        note = note?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun ServiceAttachmentInput.validated(): ServiceAttachmentInput {
    require(relativePath.isNotBlank()) { "Service attachment path cannot be blank." }
    require(!relativePath.startsWith("content://", ignoreCase = true)) {
        "Service attachments must use an app-private path."
    }
    require(displayName.isNotBlank()) { "Service attachment name cannot be blank." }
    return copy(relativePath = relativePath.trim(), displayName = displayName.trim())
}

private const val SERVICE_QUANTITY_SCALE = 3
private val SERVICE_QUANTITY_DIVISOR = BigDecimal.valueOf(1_000L)
const val MAX_SERVICE_ATTACHMENTS_PER_RECORD = 10
