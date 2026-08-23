package com.kumo.bubu.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTextWriterTest {
    @Test
    fun writesExcelCompatibleBomCrLfAndEscapedCells() {
        val csv = CsvTextWriter.write(
            headers = listOf("name", "note", "amount"),
            rows = listOf(listOf("RAV4", "A,B\"C\nD", "0")),
        )

        assertEquals(
            "\uFEFFname,note,amount\r\nRAV4,\"A,B\"\"C\nD\",0\r\n",
            csv,
        )
    }

    @Test
    fun neutralizesSpreadsheetFormulaCellsWithoutChangingNumbers() {
        val csv = CsvTextWriter.write(
            headers = listOf("note", "amount"),
            rows = listOf(listOf("=HYPERLINK(\"https://example.test\")", "-12")),
        )

        assertEquals(
            "\uFEFFnote,amount\r\n\"'=HYPERLINK(\"\"https://example.test\"\")\",-12\r\n",
            csv,
        )
    }
}
