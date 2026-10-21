package com.kumo.bubu.data.repository

import android.net.Uri
import com.kumo.bubu.domain.repository.RestoreRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue

/** Each case owns its real fault gate and assertions; only cancellation plumbing is shared. */
internal suspend fun CoroutineScope.cancelRestoreAt(
    repository: RestoreRepository,
    candidate: File,
    entered: CountDownLatch,
    release: CountDownLatch,
): Throwable? {
    val observed = CompletableDeferred<Throwable?>()
    val job = launch(Dispatchers.IO) {
        try {
            repository.restore(Uri.fromFile(candidate).toString())
            observed.complete(null)
        } catch (failure: Throwable) {
            observed.complete(failure)
        }
    }
    try {
        assertTrue("Real restore must reach the configured storage boundary", entered.await(20, TimeUnit.SECONDS))
        job.cancel(CancellationException("Fixture cancelled at storage boundary"))
    } finally {
        release.countDown()
    }
    withTimeout(20_000) { job.join() }
    return withTimeout(1_000) { observed.await() }
}
