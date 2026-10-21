package com.kumo.bubu.data.repository

import android.net.Uri
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreStorageSafetyTest {
    private lateinit var fixture: RestoreSafetyFixture

    @Before fun setUp() = runBlocking {
        fixture = RestoreSafetyFixture(ApplicationProvider.getApplicationContext(), diskBackedTarget = true)
        fixture.rememberExistingFiles()
    }

    @After fun tearDown() = runBlocking { fixture.close() }

    @Test fun realSqliteFullDuringInsertionKeepsTheCompleteOriginalAndExportableRecovery() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val sourceVehicle = fixture.source.vehicleDao().getAll().single()
        fixture.source.vehicleDao().update(sourceVehicle.copy(note = "large incoming payload".repeat(50_000)))
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val managedCount = fixture.store.listManagedRelativePaths().size
        // Limit only this fixture's SQLite file; never fill the host/device filesystem.
        val sqlite = fixture.target.openHelper.writableDatabase
        val pages = sqlite.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
        sqlite.query("PRAGMA max_page_count=$pages").use { it.moveToFirst() }
        val repository = fixture.repository()

        val failure = runCatching { repository.restore(Uri.fromFile(candidate).toString()) }.exceptionOrNull()

        // SQLite FULL may auto-rollback; framework endTransaction can then replace its
        // exception with "no transaction is active". The public contract is failure
        // with a readable complete snapshot, not a particular nested SDK exception.
        assertTrue("Page-limited restore must fail: ${failure?.stackTraceToString()}", failure is RestoreException)
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-sqlite-full.bubu"))
        val recovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
        fixture.assertSameSnapshot(original, recovery)
        assertEquals(managedCount, fixture.store.listManagedRelativePaths().size)
    }

    @Test fun enospcDuringRecoveryShortWriteKeepsThePreviousRecoveryAndOriginalSnapshot() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        fixture.seed(fixture.target, "original")
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val previous = original.copyTo(File(fixture.recoveryDirectory, "recovery-previous.bubu"))
        val repository = fixture.repository(OfflineBackupRepository(
            fixture.context, fixture.target, fixture.store, openArchiveOutput = { file ->
                object : FileOutputStream(file) {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        super.write(bytes, offset, minOf(length, 16))
                        throw IOException("No space left on device", ErrnoException("write", OsConstants.ENOSPC))
                    }
                }
            },
        ))

        val failure = runCatching { repository.restore(Uri.fromFile(candidate).toString()) }.exceptionOrNull()

        assertTrue("The injected ENOSPC must remain diagnosable", generateSequence(failure) { it.cause }
            .any { it is ErrnoException && it.errno == OsConstants.ENOSPC })
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-recovery-enospc.bubu"))
        assertEquals(previous.name, repository.getLatestRecoveryBackup()?.fileName)
        val exported = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(exported).toString())
        fixture.assertSameSnapshot(original, exported)
        assertTrue(fixture.recoveryDirectory.listFiles().orEmpty().none { it.extension == "part" })
    }

    @Test fun smallIncomingBackupCannotHideTheSpaceNeededForTheCurrentRecoverySnapshot() = runBlocking {
        fixture.seed(fixture.source, "incoming")
        val existing = fixture.store.listManagedRelativePaths().toSet()
        fixture.seed(fixture.target, "original")
        val originalPath = (fixture.store.listManagedRelativePaths().toSet() - existing).single()
        val image = File(fixture.context.filesDir, originalPath)
        val pixels = image.readBytes()
        val incompressible = ByteArray(2 * 1024 * 1024).also { java.util.Random(42).nextBytes(it) }
        image.outputStream().use { it.write(pixels); it.write(incompressible) }
        val candidate = fixture.export(fixture.source, "candidate.bubu")
        val original = fixture.export(fixture.target, "original.bubu")
        val previous = original.copyTo(File(fixture.recoveryDirectory, "recovery-previous.bubu"))
        val repository = OfflineRestoreRepository(
            fixture.context, fixture.target, fixture.store,
            OfflineBackupRepository(fixture.context, fixture.target, fixture.store), fixture.recoveryDirectory,
            availablePrivateBytes = { 256 * 1024L },
        )

        val result = runCatching { repository.restore(Uri.fromFile(candidate).toString()) }

        assertTrue("A tiny incoming file must not bypass the current 2MiB recovery requirement", result.isFailure)
        fixture.assertSameSnapshot(original, fixture.export(fixture.target, "after-space-refusal.bubu"))
        assertEquals(previous.name, repository.getLatestRecoveryBackup()?.fileName)
        val recovery = File(fixture.directory, "exported-recovery.bubu")
        repository.exportLatestRecoveryBackup(Uri.fromFile(recovery).toString())
        fixture.assertSameSnapshot(original, recovery)
    }
}
