package com.kumo.bubu.domain.model

data class CsvExportRequest(
    val vehicleIds: Set<Long> = emptySet(),
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
) {
    init {
        require(startEpochDay == null || endEpochDay == null || startEpochDay <= endEpochDay) {
            "CSV export start date cannot be after end date."
        }
    }
}
