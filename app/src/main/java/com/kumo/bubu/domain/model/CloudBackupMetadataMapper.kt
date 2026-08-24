package com.kumo.bubu.domain.model

object CloudBackupMetadataMapper {
    const val APP_PROPERTY_VALUE = "BuBu"

    fun map(
        id: String,
        fileName: String,
        createdAtEpochMillis: Long,
        modifiedAtEpochMillis: Long,
        sizeBytes: Long,
        appProperties: Map<String, String>,
    ): CloudBackup? {
        if (appProperties["app"] != APP_PROPERTY_VALUE) return null
        val formatVersion = appProperties["formatVersion"]?.toIntOrNull() ?: return null
        return CloudBackup(
            id = id,
            fileName = fileName,
            createdAtEpochMillis = appProperties["createdAt"]?.toLongOrNull() ?: createdAtEpochMillis,
            modifiedAtEpochMillis = modifiedAtEpochMillis,
            sizeBytes = sizeBytes,
            appVersion = appProperties["appVersion"].orEmpty(),
            formatVersion = formatVersion,
            vehicleCount = appProperties["vehicleCount"]?.toIntOrNull() ?: 0,
            fuelRecordCount = appProperties["fuelRecordCount"]?.toIntOrNull() ?: 0,
            maintenanceRecordCount = appProperties["maintenanceRecordCount"]?.toIntOrNull() ?: 0,
        )
    }
}
