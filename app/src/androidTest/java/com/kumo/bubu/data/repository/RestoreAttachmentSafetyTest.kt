package com.kumo.bubu.data.repository

import android.net.Uri
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreAttachmentSafetyTest {
    private lateinit var fixture: RestoreSafetyFixture

    @Before fun setUp() = runBlocking {
        fixture = RestoreSafetyFixture(ApplicationProvider.getApplicationContext())
        fixture.rememberExistingFiles()
    }

    @After fun tearDown() = runBlocking { fixture.close() }

    @Test fun cancellationDuringAttachmentWriteRemovesUndeliveredFilesAndKeepsTheOriginal() = runBlocking {
        fixture.seed(fixture.source, "incoming", attachmentCount = 2)
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val managedCount = fixture.store.listManagedRelativePaths().size
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var writeIndex = 0
        fixture.beforeRestoredWrite = {
            // Cancel the second real file after the first file has been delivered.
            if (++writeIndex == 2) {
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS)) { "Attachment write was not released." }
            }
        }
        val repository = fixture.repository()
        val failure = cancelRestoreAt(repository, candidate, entered, release)
        fixture.beforeRestoredWrite = null
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-cancel.bubu"))
        assertTrue("Cancellation must stay cancellation: ${failure?.stackTraceToString()}", failure is CancellationException)
        assertEquals("Undelivered restored bytes must not leak a managed file", managedCount,
            fixture.store.listManagedRelativePaths().size)
    }

    @Test fun enospcDuringStagingAndAgainDuringCompensationKeepsAnExportableCompleteRecovery() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val candidateBytes = candidate.readBytes()
        val original = fixture.export(fixture.target, "original.bubu")
        val managedCount = fixture.store.listManagedRelativePaths().size
        fixture.beforeRestoredWrite = { partial ->
            partial.writeBytes(ByteArray(16) { 42 })
            throw IOException("No space left on device", ErrnoException("write", OsConstants.ENOSPC))
        }
        val repository = fixture.repository()

        val failure = runCatching { repository.restore(Uri.fromFile(candidate).toString()) }.exceptionOrNull()

        fixture.beforeRestoredWrite = null
        assertTrue(failure is RestoreException)
        assertTrue("Recovery write failure must remain diagnosable", generateSequence(failure) { it.cause }
            .any { it is ErrnoException && it.errno == OsConstants.ENOSPC })
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-double-enospc.bubu"))
        val recovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
        fixture.assertSameSnapshot(original, recovery)
        org.junit.Assert.assertArrayEquals(candidateBytes, candidate.readBytes())
        assertEquals("Failed staging must remove all partial files", managedCount,
            fixture.store.listManagedRelativePaths().size)
    }

    @Test fun failedOldFileDeletionKeepsTheCommittedSnapshotAndRetriesWithoutWaitingADay() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        val existing = fixture.store.listManagedRelativePaths().toSet()
        fixture.seed(fixture.target, "original")
        val originalPath = (fixture.store.listManagedRelativePaths().toSet() - existing).single()
        val oldFile = File(fixture.context.filesDir, originalPath).canonicalFile
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        fixture.onDeleteManagedFile = { file -> if (file == oldFile) false else file.delete() }
        val repository = fixture.repository()

        repository.restore(Uri.fromFile(candidate).toString())

        fixture.assertSameSnapshot(candidate, fixture.export(fixture.target, "committed-before-cleanup-retry.bubu"))
        assertTrue("Fault must leave the old private file undeleted", oldFile.isFile)
        fixture.onDeleteManagedFile = null
        OfflineServiceRepository(
            fixture.target, fixture.target.vehicleDao(), fixture.target.fuelRecordDao(),
            fixture.target.serviceRecordDao(), fixture.target.serviceItemDao(), fixture.target.serviceTypeDao(),
            attachmentStore = fixture.store,
        ).retryPendingAttachmentDeletions()
        org.junit.Assert.assertFalse("A committed restore must retain cleanup work for immediate retry", oldFile.exists())
        fixture.assertSameSnapshot(candidate, fixture.export(fixture.target, "committed-after-cleanup-retry.bubu"))
    }
}
