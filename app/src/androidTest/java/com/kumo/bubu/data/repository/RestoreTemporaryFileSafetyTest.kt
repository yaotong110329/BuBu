package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreTemporaryFileSafetyTest {
    @Test fun anotherPreviewRetainsAnActiveRestoreCandidateEvenWhenItsTimestampAges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = RestoreSafetyFixture(context)
        fixture.rememberExistingFiles()
        val cache = File(context.cacheDir, "restore")
        val initialNames = cache.listFiles().orEmpty().map { it.name }.toSet()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val armed = AtomicBoolean(true)
        try {
            fixture.seed(fixture.source, "incoming")
            fixture.seed(fixture.target, "original")
            val candidate = fixture.export(fixture.source, "candidate.bubu")
            fixture.beforeTargetQuery = { sql, _ ->
                if (sql.startsWith("DELETE FROM ") && "`service_attachments`" in sql && armed.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.await(20, TimeUnit.SECONDS)) { "Test did not release the restore gate." }
                }
            }
            val restoring = async(Dispatchers.IO) {
                runCatching { fixture.repository().restore(Uri.fromFile(candidate).toString()) }
            }
            try {
                assertTrue(entered.await(20, TimeUnit.SECONDS))
                val active = cache.listFiles().orEmpty().single { it.name !in initialNames }
                assertTrue(active.setLastModified(System.currentTimeMillis() - 25L * 60 * 60 * 1000))
                // A second real repository instance must respect another operation's ownership.
                fixture.repository().preview(Uri.fromFile(candidate).toString())
                assertTrue("A live restore still owns its candidate despite the old timestamp", active.isFile)
            } finally {
                fixture.beforeTargetQuery = null
                release.countDown()
                withContext(NonCancellable) { restoring.await() }
            }
            restoring.await().getOrThrow()
            assertBackupArchiveEquals(candidate, fixture.export(fixture.target, "restored.bubu"))
        } finally {
            fixture.beforeTargetQuery = null
            release.countDown()
            withContext(NonCancellable) { fixture.close() }
        }
    }
}
