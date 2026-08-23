package com.kumo.bubu.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.kumo.bubu.data.csv.CsvExportSource
import com.kumo.bubu.data.csv.CsvExportTables
import com.kumo.bubu.data.csv.CsvReportZipWriter
import com.kumo.bubu.data.local.dao.ExpenseRecordDao
import com.kumo.bubu.data.local.dao.FuelRecordDao
import com.kumo.bubu.data.local.dao.ServiceAttachmentDao
import com.kumo.bubu.data.local.dao.ServiceItemDao
import com.kumo.bubu.data.local.dao.ServiceRecordDao
import com.kumo.bubu.data.local.dao.VehicleDao
import com.kumo.bubu.data.local.dao.VehicleReminderDao
import com.kumo.bubu.domain.repository.CsvExportRepository
import com.kumo.bubu.domain.repository.CsvExportResult
import com.kumo.bubu.domain.model.CsvExportRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineCsvExportRepository(
    context: Context,
    private val vehicleDao: VehicleDao,
    private val fuelRecordDao: FuelRecordDao,
    private val serviceRecordDao: ServiceRecordDao,
    private val serviceItemDao: ServiceItemDao,
    private val expenseRecordDao: ExpenseRecordDao,
    private val reminderDao: VehicleReminderDao,
    private val serviceAttachmentDao: ServiceAttachmentDao,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : CsvExportRepository {
    private val applicationContext = context.applicationContext
    private val cacheDirectory = File(applicationContext.cacheDir, CACHE_DIRECTORY)

    override suspend fun export(request: CsvExportRequest, destinationUriString: String): CsvExportResult =
        withContext(Dispatchers.IO) {
            val destinationUri = Uri.parse(destinationUriString)
            if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
                throw CsvExportException("Unable to create private export storage.")
            }
            val fileName = "bubu-export-${now().format(FILE_NAME_TIME_FORMAT)}.zip"
            val partial = File(cacheDirectory, ".${UUID.randomUUID()}.part")
            try {
                val source = CsvExportSource(
                    vehicles = vehicleDao.getAll(),
                    fuelRecords = fuelRecordDao.getAllForExport(),
                    serviceRecords = serviceRecordDao.getAllForExport(),
                    serviceItems = serviceItemDao.getAllForExport(),
                    expenseRecords = expenseRecordDao.getAllForExport(),
                    reminders = reminderDao.getAll(),
                    serviceAttachments = serviceAttachmentDao.getAllForExport(),
                )
                FileOutputStream(partial).use { output ->
                    CsvReportZipWriter.write(
                        destination = output,
                        tables = CsvExportTables.build(source, request),
                        readme = CsvExportTables.readme(),
                    )
                    output.fd.sync()
                }
                verifyZip(partial)
                FileInputStream(partial).use { input ->
                    requireNotNull(applicationContext.contentResolver.openOutputStream(destinationUri, "w")) {
                        "Selected export destination cannot be opened."
                    }.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                CsvExportResult(fileName = fileName, byteCount = partial.length())
            } catch (error: CsvExportException) {
                deleteIncompleteDestination(destinationUri)
                throw error
            } catch (error: Throwable) {
                deleteIncompleteDestination(destinationUri)
                throw CsvExportException("Unable to create CSV export.", error)
            } finally {
                partial.delete()
            }
        }

    private fun deleteIncompleteDestination(destinationUri: Uri) {
        runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, destinationUri) }
    }

    private fun verifyZip(file: File) {
        val names = ZipInputStream(FileInputStream(file)).use { zip ->
            buildList {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.name)
                }
            }
        }
        if (names != EXPECTED_ENTRY_NAMES) {
            throw CsvExportException("CSV export validation failed.")
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "csv-export"
        val FILE_NAME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        val EXPECTED_ENTRY_NAMES = listOf(
            "vehicles.csv",
            "fuel_records.csv",
            "service_records.csv",
            "service_items.csv",
            "expense_records.csv",
            "odometer_corrections.csv",
            "reminders.csv",
            "attachments.csv",
            "README.txt",
        )
    }
}

class CsvExportException(message: String, cause: Throwable? = null) : Exception(message, cause)
