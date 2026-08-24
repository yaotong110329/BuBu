package com.kumo.bubu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudBackupMetadataMapperTest {
    @Test
    fun mapsDrivePropertiesToTheDomainBackup() {
        val backup = CloudBackupMetadataMapper.map(
            id = "drive-id",
            fileName = "bubu-backup-2026-08-24-163000.bubu",
            createdAtEpochMillis = 2,
            modifiedAtEpochMillis = 3,
            sizeBytes = 4,
            appProperties = mapOf(
                "app" to "BuBu",
                "formatVersion" to "1",
                "appVersion" to "1.1.0",
                "createdAt" to "1",
                "vehicleCount" to "2",
                "fuelRecordCount" to "168",
                "maintenanceRecordCount" to "41",
            ),
        )

        requireNotNull(backup)
        assertEquals("drive-id", backup.id)
        assertEquals(2, backup.vehicleCount)
        assertEquals(168, backup.fuelRecordCount)
        assertEquals(41, backup.maintenanceRecordCount)
    }

    @Test
    fun ignoresFilesThatWereNotCreatedByBuBu() {
        assertNull(
            CloudBackupMetadataMapper.map(
                id = "other",
                fileName = "other.bubu",
                createdAtEpochMillis = 1,
                modifiedAtEpochMillis = 1,
                sizeBytes = 1,
                appProperties = mapOf("app" to "Other", "formatVersion" to "1"),
            ),
        )
    }
}
