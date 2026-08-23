package com.kumo.bubu.data.csv

import java.io.OutputStream
import java.io.FilterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CsvTable(
    val fileName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
)

object CsvReportZipWriter {
    fun write(
        destination: OutputStream,
        tables: List<CsvTable>,
        readme: String,
    ) {
        require(tables.map(CsvTable::fileName).distinct().size == tables.size) {
            "CSV ZIP contains duplicate file names."
        }
        require(tables.all { it.fileName.endsWith(CSV_SUFFIX) }) {
            "CSV ZIP tables must use .csv file names."
        }
        ZipOutputStream(NonClosingOutputStream(destination)).use { zip ->
            tables.forEach { table ->
                zip.putNextEntry(ZipEntry(table.fileName))
                zip.write(CsvTextWriter.write(table.headers, table.rows).encodeToByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(README_FILE_NAME))
            zip.write(readme.toCrLfTerminated().encodeToByteArray())
            zip.closeEntry()
        }
    }

    private fun String.toCrLfTerminated(): String =
        replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n").let { text ->
            if (text.endsWith("\r\n")) text else "$text\r\n"
        }

    private const val CSV_SUFFIX = ".csv"
    private const val README_FILE_NAME = "README.txt"

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() {
            flush()
        }
    }
}
