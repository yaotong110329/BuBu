package com.kumo.bubu.data.repository

import com.kumo.bubu.domain.model.CloudBackup
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateCloudDownloadStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val backup = CloudBackup("test", "test.bubu", 1, 1, 1, "1.1.1", 1, 0, 0, 0)

    @Test fun cancellationAfterIoCompletesButBeforeDeliveryRemovesTheDownload() = runTest {
        val ioTasks = ArrayDeque<Runnable>()
        val ioDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { ioTasks.addLast(block) }
        }
        val directory = temporary.newFolder("downloads")
        val store = PrivateCloudDownloadStore(directory, ioDispatcher)
        val request = async { store.create { file -> file.writeText("validated"); backup } }
        runCurrent()
        assertEquals(1, ioTasks.size)
        ioTasks.removeFirst().run()
        assertEquals(1, directory.listFiles()!!.size)
        request.cancel()
        runCurrent()
        assertTrue(request.isCancelled)
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test fun failedValidationRemovesOnlyTheFailedOperation() = runTest {
        val directory = temporary.newFolder("downloads")
        val unrelated = File(directory, "download-unowned.bubu").apply { writeText("original") }
        val store = PrivateCloudDownloadStore(directory, StandardTestDispatcher(testScheduler))
        val failure = runCatching { store.create { file -> file.writeText("bad archive"); error("Invalid archive") } }
        assertTrue(failure.isFailure)
        assertEquals(listOf(unrelated), directory.listFiles()!!.toList())
        assertEquals("original", unrelated.readText())
    }

    @Test fun successfulOperationsHaveIndependentFilesAndOnlyOwnedFilesCanBeRemoved() = runTest {
        val directory = temporary.newFolder("downloads")
        val store = PrivateCloudDownloadStore(directory, StandardTestDispatcher(testScheduler))
        val first = store.create { file -> file.writeText("first"); backup }
        val second = store.create { file -> file.writeText("second"); backup }
        val original = temporary.newFile("source.bubu").apply { writeText("source") }
        store.discard(first.copy(localFilePath = original.path))
        assertEquals("source", original.readText())
        store.discard(first)
        store.discard(first)
        assertFalse(File(first.localFilePath).exists())
        assertEquals("second", File(second.localFilePath).readText())
        store.discard(second)
    }
}
