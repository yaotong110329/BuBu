package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import android.os.Process
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.core.database.BuBuDatabase
import com.kumo.bubu.data.attachment.PrivateAttachmentStore
import com.kumo.bubu.data.backup.BackupArchiveReader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreProcessRestartProbeTest {
    @Test fun prepareUntilExternalTermination() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = RestoreRestartProbeFiles.fromArguments(context)
        val fixture = RestoreSafetyFixture(context, true, RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        fixture.rememberExistingFiles()
        val initialPaths = fixture.store.listManagedRelativePaths()
        val identity: (String) -> String = { UUID.nameUUIDFromBytes((files.runId + it).encodeToByteArray()).toString() }
        try {
            fixture.seed(fixture.source, "incoming", 2, identity)
            val pathsAfterSource = fixture.store.listManagedRelativePaths().toSet()
            fixture.seed(fixture.target, "original", 2, identity)
            val oldPaths = fixture.store.listManagedRelativePaths().filterNot { it in pathsAfterSource }
            assertEquals(2, oldPaths.size)
            val candidate = fixture.export(fixture.source, "candidate.bubu")
            fixture.export(fixture.target, "original.bubu")
            val run = RestoreRestartRun(files.runId, files.phase, Process.myPid(),
                requireNotNull(fixture.targetName), fixture.directory.name, initialPaths, oldPaths,
                files.restoreCacheDirectory.listFiles().orEmpty().map { it.name })
            files.save(run)
            if (files.phase == "during-cleanup") {
                val oldFiles = oldPaths.associateBy { File(context.filesDir, it).canonicalFile }
                fixture.onDeleteManagedFile = { file ->
                    val deleted = file.delete()
                    val oldPath = oldFiles[file.canonicalFile]
                    if (deleted && oldPath != null) {
                        val interrupted = run.copy(removedOldManagedPath = oldPath)
                        files.save(interrupted)
                        files.waitForHostTermination(interrupted)
                    }
                    deleted
                }
            }
            val tables = setOf("vehicles", "fuel_records", "service_types", "service_records", "service_items",
                "expense_records", "vehicle_reminders", "vehicle_service_reminder_preferences", "service_attachments")
            var replacementInserted = false
            fixture.beforeTargetQuery = { sql, arguments ->
                if (sql.startsWith("INSERT") && "`service_attachments`" in sql &&
                    identity("incoming-attachment-0") in arguments) replacementInserted = true
                if (files.phase == "before-clear" && sql.startsWith("DELETE FROM ") && tables.any { "`$it`" in sql }) {
                    files.waitForHostTermination(run)
                }
                if (files.phase == "in-transaction" && sql.startsWith("INSERT") &&
                    "`service_attachments`" in sql && identity("incoming-attachment-0") in arguments) {
                    files.waitForHostTermination(run)
                }
                if (files.phase == "after-commit" && replacementInserted &&
                    sql == "SELECT * FROM pending_attachment_deletions ORDER BY id ASC") {
                    files.waitForHostTermination(run)
                }
            }
            fixture.repository().restore(Uri.fromFile(candidate).toString())
            fail("Host termination gate was not reached.")
        } finally {
            // A genuine process kill cannot run this. Normal failures still clean their own fixture.
            fixture.close()
        }
    }

    @Test fun verifyAfterExternalTermination() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = RestoreRestartProbeFiles.fromArguments(context)
        val run = files.read()
        assertNotEquals("The original process must have died", run.preparedPid, Process.myPid())
        val directory = files.workDirectory(run)
        val original = File(directory, "original.bubu")
        val database = Room.databaseBuilder(context, BuBuDatabase::class.java, run.databaseName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
        val store = PrivateAttachmentStore(context, "attachment")
        val backup = OfflineBackupRepository(context, database, store)
        val restore = OfflineRestoreRepository(context, database, store, backup, File(directory, "recovery"))
        suspend fun export(name: String): File = File(directory, name).also {
            backup.createBackup(Uri.fromFile(it).toString())
        }
        try {
            if (files.phase == "during-cleanup") {
                val removed = requireNotNull(run.removedOldManagedPath)
                assertNull(store.readManagedBytes(removed))
                assertNotNull(store.readManagedBytes(run.oldManagedPaths.single { it != removed }))
            }
            // Observe before any recovery or cleanup: reopening must not silently redo a restore.
            val after = export("reopened.bubu")
            val expected = if (files.phase in setOf("after-commit", "during-cleanup")) File(directory, "candidate.bubu") else original
            assertBackupArchiveEquals(expected, after)
            val data = BackupArchiveReader.read(after).data
            val collections = listOf(data.vehicles.map { it.publicId }, data.fuelRecords.map { it.publicId },
                data.serviceTypes.map { it.publicId }, data.serviceRecords.map { it.publicId },
                data.serviceItems.map { it.publicId }, data.expenseRecords.map { it.publicId },
                data.reminders.map { it.publicId }, data.serviceReminderPreferences.map { it.publicId },
                data.attachments.map { it.publicId })
            assertEquals(listOf(1, 1, 1, 1, 1, 1, 1, 1, 2), collections.map { it.size })
            collections.flatten().forEach { assertEquals(it, UUID.fromString(it).toString()) }
            val recovery = File(directory, "exported-original-recovery.bubu")
            restore.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
            assertBackupArchiveEquals(original, recovery)

            val abandonedCandidate = files.restoreCandidate(run)
            assertBackupArchiveEquals(File(directory, "candidate.bubu"), abandonedCandidate)
            val oldTime = System.currentTimeMillis() - 25L * 60 * 60 * 1000
            assertTrue(abandonedCandidate.setLastModified(oldTime))
            val freshCandidate = File(files.restoreCacheDirectory, ".${UUID.randomUUID()}.bubu")
            val unrelated = File(files.restoreCacheDirectory, "keep-${files.runId}.bubu")
            val unrelatedBytes = byteArrayOf(0x42, 0x75, 0x42, 0x75)
            File(directory, "candidate.bubu").copyTo(freshCandidate)
            unrelated.writeBytes(unrelatedBytes)
            assertTrue(unrelated.setLastModified(oldTime))
            try {
                restore.preview(Uri.fromFile(File(directory, "candidate.bubu")).toString())
                assertFalse("Abandoned private candidate must be reclaimed on the next preview", abandonedCandidate.exists())
                assertTrue("Fresh candidate must retain its grace period", freshCandidate.isFile)
                assertArrayEquals(unrelatedBytes, unrelated.readBytes())
            } finally {
                freshCandidate.delete()
                unrelated.delete()
            }

            // Age only this probe's files to exercise the existing orphan grace policy.
            val owned = store.listManagedRelativePaths().toSet() - run.initialManagedPaths.toSet()
            owned.forEach { assertTrue(File(context.filesDir, it).setLastModified(oldTime)) }
            OfflineServiceRepository(database, database.vehicleDao(), database.fuelRecordDao(),
                database.serviceRecordDao(), database.serviceItemDao(), database.serviceTypeDao(),
                attachmentStore = store).retryPendingAttachmentDeletions()
            assertBackupArchiveEquals(after, export("after-orphan-cleanup.bubu"))
            assertEquals(2, (store.listManagedRelativePaths().toSet() - run.initialManagedPaths.toSet()).size)

            // Explicit manual recovery, after the first observable snapshot has been verified.
            restore.restore(Uri.fromFile(recovery).toString())
            assertBackupArchiveEquals(original, export("after-manual-recovery.bubu"))
            files.saveReport(buildJsonObject {
                put("runId", files.runId); put("phase", files.phase)
                put("killedPid", run.preparedPid); put("reopenedPid", Process.myPid())
                put("observedSnapshot", data.vehicles.single().name); put("collectionCount", 9)
                put("stableIdentityCount", collections.sumOf { it.size }); put("attachmentCount", 2)
                put("exactAttachmentBytesVerified", true); put("safeCleanupVerified", true)
                put("recoveryVerified", true); put("manualRecoveryVerified", true)
                put("partialOldCleanupVerified", files.phase == "during-cleanup")
                put("temporaryCopyCleanupVerified", true)
            })
        } finally {
            database.close()
            context.deleteDatabase(run.databaseName)
            (store.listManagedRelativePaths().toSet() - run.initialManagedPaths.toSet()).forEach { store.deleteManagedFile(it) }
            directory.deleteRecursively()
        }
    }
}
