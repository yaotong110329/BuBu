package com.kumo.bubu.data.repository

import com.kumo.bubu.data.backup.BackupArchiveReader
import com.kumo.bubu.data.backup.BackupData
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals

/** Compare the formal backup contract, including every referenced attachment's contents. */
internal fun assertBackupArchiveEquals(expected: File, actual: File) {
    val left = BackupArchiveReader.read(expected).data
    assertEquals(left.inPublicIdentityOrder(), BackupArchiveReader.read(actual).data.inPublicIdentityOrder())
    ZipFile(expected).use { a ->
        ZipFile(actual).use { b ->
            left.attachments.forEach { attachment ->
                assertArrayEquals(
                    a.getInputStream(a.getEntry(attachment.archivePath)).use { it.readBytes() },
                    b.getInputStream(b.getEntry(attachment.archivePath)).use { it.readBytes() },
                )
            }
        }
    }
}

// Local row IDs can change on overwrite. Explicit sequence/sort fields remain in the comparison.
private fun BackupData.inPublicIdentityOrder(): BackupData = copy(
    vehicles = vehicles.sortedBy { it.publicId },
    fuelRecords = fuelRecords.sortedBy { it.publicId },
    serviceTypes = serviceTypes.sortedBy { it.publicId },
    serviceRecords = serviceRecords.sortedBy { it.publicId },
    serviceItems = serviceItems.sortedBy { it.publicId },
    expenseRecords = expenseRecords.sortedBy { it.publicId },
    reminders = reminders.sortedBy { it.publicId },
    serviceReminderPreferences = serviceReminderPreferences.sortedBy { it.publicId },
    attachments = attachments.sortedBy { it.publicId },
)
