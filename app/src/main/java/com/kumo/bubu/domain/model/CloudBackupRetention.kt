package com.kumo.bubu.domain.model

object CloudBackupRetention {
    const val MAX_BACKUPS = 5

    fun backupsToDelete(backups: List<CloudBackup>, maxBackups: Int = MAX_BACKUPS): List<CloudBackup> {
        require(maxBackups > 0)
        return backups.sortedByDescending(CloudBackup::createdAtEpochMillis).drop(maxBackups)
    }
}

object CloudBackupOrdering {
    fun newestFirst(backups: List<CloudBackup>): List<CloudBackup> =
        backups.sortedByDescending(CloudBackup::createdAtEpochMillis)
}
