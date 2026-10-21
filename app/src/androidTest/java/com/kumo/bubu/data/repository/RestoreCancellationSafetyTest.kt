package com.kumo.bubu.data.repository

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreCancellationSafetyTest {
    private lateinit var fixture: RestoreSafetyFixture

    @Before fun setUp() = runBlocking {
        fixture = RestoreSafetyFixture(ApplicationProvider.getApplicationContext())
        fixture.rememberExistingFiles()
    }

    @After fun tearDown() = runBlocking { fixture.close() }

    @Test fun cancellationDuringRecoveryWriteKeepsTheCompleteOriginalAndPropagatesCancellation() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        original.copyTo(File(fixture.recoveryDirectory, "recovery-previous.bubu"))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repository = fixture.repository(OfflineBackupRepository(
            fixture.context, fixture.target, fixture.store, openArchiveOutput = { file ->
                object : FileOutputStream(file) {
                    private var blocked = false
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        if (!blocked) {
                            blocked = true
                            entered.countDown()
                            check(release.await(20, TimeUnit.SECONDS)) { "Recovery write was not released." }
                        }
                        super.write(bytes, offset, length)
                    }
                }
            },
        ))
        val failure = cancelRestoreAt(repository, candidate, entered, release)
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-cancel.bubu"))
        val exportedRecovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(exportedRecovery).toString())
        fixture.assertSameSnapshot(original, exportedRecovery)
        assertTrue("Cancellation must stay cancellation, received ${failure?.stackTraceToString()}", failure is CancellationException)
    }

    @Test fun cancellationDuringReplacementKeepsTheOriginalAttachmentsAndPropagatesCancellation() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val managedCount = fixture.store.listManagedRelativePaths().size
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.beforeTargetQuery = { sql, arguments ->
            if (sql.startsWith("INSERT") && sql.contains("`vehicles`") && "incoming-vehicle" in arguments) {
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS)) { "Replacement insert was not released." }
            }
        }
        val repository = fixture.repository()
        val failure = cancelRestoreAt(repository, candidate, entered, release)
        fixture.beforeTargetQuery = null
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-transaction-cancel.bubu"))
        val recovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
        fixture.assertSameSnapshot(original, recovery)
        assertTrue("Cancellation must stay cancellation, received ${failure?.stackTraceToString()}", failure is CancellationException)
        assertEquals("Undelivered staged attachments must be removed", managedCount,
            fixture.store.listManagedRelativePaths().size)
    }

    @Test fun cancellationAfterStagingKeepsTheOriginalAndRemovesUnreferencedFiles() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val managedCount = fixture.store.listManagedRelativePaths().size
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var intercepted = false
        fixture.beforeTargetQuery = { sql, _ ->
            if (!intercepted && sql == "SELECT relativePath FROM service_attachments") {
                intercepted = true
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS)) { "Attachment ownership read was not released." }
            }
        }
        val repository = fixture.repository()
        val failure = cancelRestoreAt(repository, candidate, entered, release)
        fixture.beforeTargetQuery = null
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-staging-cancel.bubu"))
        assertTrue("Cancellation must stay cancellation, received ${failure?.stackTraceToString()}", failure is CancellationException)
        assertEquals("No undelivered attachment may survive cancellation", managedCount,
            fixture.store.listManagedRelativePaths().size)
    }
}
