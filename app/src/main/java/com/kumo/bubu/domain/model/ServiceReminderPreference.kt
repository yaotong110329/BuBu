package com.kumo.bubu.domain.model

/** A vehicle-specific interval policy; service history remains the source of last completion. */
data class ServiceReminderPreference(
    val id: Long,
    val publicId: String,
    val vehicleId: Long,
    val serviceTypeId: Long,
    val isEnabled: Boolean,
    val intervalKm: Long?,
    val baseOdometerKm: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ServiceReminderPreferenceInput(
    val vehicleId: Long,
    val serviceTypeId: Long,
    val isEnabled: Boolean,
    val intervalKm: Long?,
    val baseOdometerKm: Long? = null,
    val sortOrder: Int,
)

fun ServiceReminderPreferenceInput.validated(): ServiceReminderPreferenceInput {
    require(vehicleId > 0) { "A vehicle is required." }
    require(serviceTypeId > 0) { "A service type is required." }
    require(intervalKm == null || intervalKm > 0) { "Reminder interval must be positive." }
    require(baseOdometerKm == null || baseOdometerKm >= 0) { "Reminder base odometer cannot be negative." }
    require(sortOrder >= 0) { "Reminder sort order cannot be negative." }
    return this
}
