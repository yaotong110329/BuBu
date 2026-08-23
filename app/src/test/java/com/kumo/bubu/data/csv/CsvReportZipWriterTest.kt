package com.kumo.bubu.data.csv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvReportZipWriterTest {
    @Test
    fun writesNamedCsvTablesAndTraditionalChineseReadme() {
        val destination = ByteArrayOutputStream()

        CsvReportZipWriter.write(
            destination = destination,
            tables = listOf(
                CsvTable("vehicles.csv", listOf("vehicle_ref", "name"), listOf(listOf("VEH-001", "RAV4"))),
                CsvTable("fuel_records.csv", listOf("fuel_ref", "vehicle_ref"), emptyList()),
            ),
            readme = "BuBu CSV 匯出說明",
        )

        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(destination.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().decodeToString()
            }
        }

        assertEquals(listOf("vehicles.csv", "fuel_records.csv", "README.txt"), entries.keys.toList())
        assertEquals("\uFEFFvehicle_ref,name\r\nVEH-001,RAV4\r\n", entries.getValue("vehicles.csv"))
        assertEquals("BuBu CSV 匯出說明\r\n", entries.getValue("README.txt"))
    }

    @Test
    fun leavesTheCallerOutputStreamOpenForFinalSync() {
        val destination = File.createTempFile("csv-report", ".zip")
        try {
            FileOutputStream(destination).use { output ->
                CsvReportZipWriter.write(
                    destination = output,
                    tables = listOf(CsvTable("vehicles.csv", listOf("name"), listOf(listOf("測試汽車")))),
                    readme = "README",
                )

                output.fd.sync()
            }
        } finally {
            destination.delete()
        }
    }
}
