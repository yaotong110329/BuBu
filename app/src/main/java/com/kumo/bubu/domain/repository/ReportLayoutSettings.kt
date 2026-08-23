package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.ReportLayout
import kotlinx.coroutines.flow.Flow

interface ReportLayoutSettings {
    fun observeLayout(): Flow<ReportLayout>

    suspend fun saveLayout(layout: ReportLayout)
}
