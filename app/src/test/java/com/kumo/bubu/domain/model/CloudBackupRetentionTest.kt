package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBackupRetentionTest {
    @Test
    fun keepsTheFiveNewestBackups() {
        val backups = (1L..7L).map(::backup)

        assertEquals(listOf("2", "1"), CloudBackupRetention.backupsToDelete(backups).map(CloudBackup::id))
    }

    @Test
    fun sortsBackupListsNewestFirst() {
        val ordered = CloudBackupOrdering.newestFirst(listOf(backup(2), backup(8), backup(4)))

        assertEquals(listOf("8", "4", "2"), ordered.map(CloudBackup::id))
    }

    private fun backup(createdAt: Long) = CloudBackup(
        id = createdAt.toString(),
        fileName = "bubu-backup-$createdAt.bubu",
        createdAtEpochMillis = createdAt,
        modifiedAtEpochMillis = createdAt,
        sizeBytes = 1,
        appVersion = "1.0.0",
        formatVersion = 1,
        vehicleCount = 0,
        fuelRecordCount = 0,
        maintenanceRecordCount = 0,
    )
}
