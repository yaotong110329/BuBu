package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.CsvExportRequest

data class CsvExportResult(
    val fileName: String,
    val byteCount: Long,
)

interface CsvExportRepository {
    suspend fun export(request: CsvExportRequest, destinationUriString: String): CsvExportResult
}
