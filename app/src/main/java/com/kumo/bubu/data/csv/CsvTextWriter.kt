package com.kumo.bubu.data.csv

object CsvTextWriter {
    fun write(headers: List<String>, rows: List<List<String>>): String {
        require(headers.isNotEmpty()) { "CSV requires at least one header." }
        require(rows.all { it.size == headers.size }) { "Every CSV row must match the header count." }
        return buildString {
            append('\uFEFF')
            appendRow(headers, neutralizeFormula = false)
            rows.forEach { row -> appendRow(row, neutralizeFormula = true) }
        }
    }

    private fun StringBuilder.appendRow(cells: List<String>, neutralizeFormula: Boolean) {
        append(cells.joinToString(",") { cell -> escape(cell, neutralizeFormula) })
        append("\r\n")
    }

    private fun escape(value: String, neutralizeFormula: Boolean): String {
        val protected = if (neutralizeFormula && value.requiresFormulaProtection()) "'$value" else value
        val requiresQuotes = protected.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (requiresQuotes) "\"${protected.replace("\"", "\"\"")}\"" else protected
    }

    private fun String.requiresFormulaProtection(): Boolean {
        val firstMeaningful = firstOrNull { !it.isWhitespace() } ?: return false
        if (firstMeaningful !in FORMULA_PREFIXES) return false
        return !matches(PLAIN_NUMBER)
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    private val PLAIN_NUMBER = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?")
}
