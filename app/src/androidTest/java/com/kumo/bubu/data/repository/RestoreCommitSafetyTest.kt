package com.kumo.bubu.data.repository

import android.net.Uri
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.data.backup.BackupArchiveReader
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreCommitSafetyTest {
    private lateinit var fixture: RestoreSafetyFixture

    @Before fun setUp() = runBlocking {
        fixture = RestoreSafetyFixture(ApplicationProvider.getApplicationContext(), diskBackedTarget = true)
        fixture.rememberExistingFiles()
    }

    @After fun tearDown() = runBlocking { fixture.close() }

    @Test fun commitBoundaryCancellationAndFailedCompensationKeepACompleteReadableSnapshot() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val depth = ThreadLocal.withInitial { 0 }
        var replacing = false
        var intercepted = false
        // Room 2.8.4 invokes this before the real outer endTransaction/commit.
        // Tracking nesting selects that boundary rather than an inner DAO transaction.
        fixture.beforeTargetQuery = { sql, arguments ->
            when {
                sql.startsWith("BEGIN") -> depth.set((depth.get() ?: 0) + 1)
                sql.startsWith("INSERT") && sql.contains("`vehicles`") && "incoming-vehicle" in arguments -> replacing = true
                sql == "END TRANSACTION" -> {
                    if (replacing && depth.get() == 1 && !intercepted) {
                        intercepted = true
                        entered.countDown()
                        check(release.await(20, TimeUnit.SECONDS)) { "Outer commit was not released." }
                        fixture.beforeRestoredWrite = {
                            throw IOException("No space left on device", ErrnoException("write", OsConstants.ENOSPC))
                        }
                    }
                    depth.set((depth.get() ?: 0) - 1)
                }
            }
        }
        val repository = fixture.repository()
        val failure = cancelRestoreAt(repository, candidate, entered, release)
        fixture.beforeTargetQuery = null
        fixture.beforeRestoredWrite = null
        assertTrue("Cancellation/failure must be returned", failure is CancellationException || failure is RestoreException)
        // Commit may win the cancellation race. Either complete snapshot is safe;
        // re-export must read every attachment, even when automatic recovery cannot write.
        val after = fixture.export(fixture.target, "after-commit-cancel.bubu")
        val expected = if (BackupArchiveReader.read(after).data == BackupArchiveReader.read(original).data) original else candidate
        fixture.assertSameSnapshot(expected, after)
        val recovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
        fixture.assertSameSnapshot(original, recovery)
    }

    @Test fun cancellationDuringCommittedFileCleanupKeepsOneCompleteSnapshotAndTheRecovery() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        val existing = fixture.store.listManagedRelativePaths().toSet()
        fixture.seed(fixture.target, "original")
        val oldPath = (fixture.store.listManagedRelativePaths().toSet() - existing).single()
        val oldFile = File(fixture.context.filesDir, oldPath).canonicalFile
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.onDeleteManagedFile = { file ->
            if (file == oldFile) {
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS)) { "Committed file cleanup was not released." }
            }
            file.delete()
        }
        val repository = fixture.repository()
        val failure = cancelRestoreAt(repository, candidate, entered, release)
        fixture.onDeleteManagedFile = null
        assertTrue("Cancellation must remain cancellation: ${failure?.stackTraceToString()}", failure is CancellationException)
        val after = fixture.export(fixture.target, "after-cleanup-cancel.bubu")
        val expected = if (BackupArchiveReader.read(after).data == BackupArchiveReader.read(original).data) original else candidate
        fixture.assertSameSnapshot(expected, after)
        val recovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
        fixture.assertSameSnapshot(original, recovery)
    }
}
