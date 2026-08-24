package com.kumo.bubu.domain.model

data class CloudBackup(
    val id: String,
    val fileName: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val sizeBytes: Long,
    val appVersion: String,
    val formatVersion: Int,
    val vehicleCount: Int,
    val fuelRecordCount: Int,
    val maintenanceRecordCount: Int,
)

data class CloudBackupAccount(
    val email: String,
    val lastCloudBackupAtEpochMillis: Long? = null,
)

sealed interface CloudBackupConnection {
    data object NotConnected : CloudBackupConnection
    data class Connected(val account: CloudBackupAccount) : CloudBackupConnection
    data object NeedsAuthorization : CloudBackupConnection
}

sealed interface CloudBackupError {
    data object NotConnected : CloudBackupError
    data object NetworkUnavailable : CloudBackupError
    data object AuthorizationExpired : CloudBackupError
    data object UploadFailed : CloudBackupError
    data object DownloadFailed : CloudBackupError
    data object DeleteFailed : CloudBackupError
    data object InvalidBackup : CloudBackupError
    data object UnsupportedFormat : CloudBackupError
    data object ConfigurationMissing : CloudBackupError
    data class Unknown(val message: String?) : CloudBackupError
}
