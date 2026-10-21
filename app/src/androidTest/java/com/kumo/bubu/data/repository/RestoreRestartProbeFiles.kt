package com.kumo.bubu.data.repository

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assume.assumeTrue

@Serializable
internal data class RestoreRestartRun(
    val runId: String,
    val phase: String,
    val preparedPid: Int,
    val databaseName: String,
    val directoryName: String,
    val initialManagedPaths: List<String>,
    val oldManagedPaths: List<String>,
    val initialRestoreCandidateNames: List<String>,
    val removedOldManagedPath: String? = null,
    val restoreCandidateName: String? = null,
)

/** Host-coordinated protocol lives only in AndroidTest, never the product runtime. */
internal class RestoreRestartProbeFiles private constructor(
    private val context: Context,
    val runId: String,
    val phase: String,
) {
    val restoreCacheDirectory = File(context.cacheDir, "restore")
    private val directory = File(context.filesDir, "restore-restart-probe").apply { check(isDirectory || mkdirs()) }
    private val manifest = File(directory, "$runId-manifest.json")
    private val gate = File(directory, "$runId-gate.json")
    private val report = File(directory, "$runId-report.json")

    init {
        require(UUID.fromString(runId).toString() == runId)
        require(phase in setOf("before-clear", "in-transaction", "after-commit", "during-cleanup"))
    }

    fun save(run: RestoreRestartRun) = write(manifest, Json.encodeToString(RestoreRestartRun.serializer(), run))

    fun read(): RestoreRestartRun = Json.decodeFromString(RestoreRestartRun.serializer(), manifest.readText()).also {
        require(it.runId == runId && it.phase == phase)
        require(it.databaseName.matches(Regex("restore-safety-[a-f0-9-]{36}\\.db")))
        require(it.oldManagedPaths.size == 2)
        it.oldManagedPaths.forEach { path ->
            require(path.matches(Regex("attachments/service/[a-f0-9-]{36}\\.png")))
            require(path !in it.initialManagedPaths)
        }
        require(it.removedOldManagedPath == null || it.removedOldManagedPath in it.oldManagedPaths)
        require(requireNotNull(it.restoreCandidateName).matches(Regex("\\.[a-f0-9-]{36}\\.bubu")))
        require(it.restoreCandidateName !in it.initialRestoreCandidateNames)
    }

    fun workDirectory(run: RestoreRestartRun): File = File(context.cacheDir, run.directoryName).canonicalFile.also {
        require(it.parentFile == context.cacheDir.canonicalFile)
        require(it.name.matches(Regex("restore-safety-[a-f0-9-]{36}")))
    }

    fun waitForHostTermination(run: RestoreRestartRun): Nothing {
        val candidate = restoreCacheDirectory.listFiles().orEmpty().single {
            it.isFile && it.name !in run.initialRestoreCandidateNames &&
                it.name.matches(Regex("\\.[a-f0-9-]{36}\\.bubu"))
        }
        val interrupted = run.copy(restoreCandidateName = candidate.name)
        save(interrupted)
        write(gate, Json.encodeToString(RestoreRestartRun.serializer(), interrupted))
        CountDownLatch(1).await(90, TimeUnit.SECONDS)
        error("Host did not terminate the probe process at the storage boundary.")
    }

    fun saveReport(value: JsonObject) = write(report, value.toString())

    fun restoreCandidate(run: RestoreRestartRun): File =
        File(restoreCacheDirectory, requireNotNull(run.restoreCandidateName)).canonicalFile.also {
            require(it.parentFile == restoreCacheDirectory.canonicalFile)
        }

    private fun write(file: File, value: String) {
        FileOutputStream(file).use { output ->
            output.write(value.encodeToByteArray())
            output.fd.sync()
        }
    }

    companion object {
        fun fromArguments(context: Context): RestoreRestartProbeFiles {
            val arguments = InstrumentationRegistry.getArguments()
            val id = arguments.getString("bubu.restoreDeath.runId")
            val phase = arguments.getString("bubu.restoreDeath.phase")
            assumeTrue("Requires the guarded host process-death driver", id != null && phase != null)
            return RestoreRestartProbeFiles(context, requireNotNull(id), requireNotNull(phase))
        }
    }
}
