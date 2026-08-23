package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.ReportData
import com.kumo.bubu.domain.model.ReportQuery
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun observeReport(query: ReportQuery): Flow<ReportData>
}
