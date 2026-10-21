package com.kumo.bubu.domain.repository

data class MaintenanceSummaryNotificationState(
    val alertedSignature: String? = null,
    val renderedSignature: String? = null,
)

interface MaintenanceSummaryStateStore {
    suspend fun read(): MaintenanceSummaryNotificationState
    suspend fun write(state: MaintenanceSummaryNotificationState)
}
