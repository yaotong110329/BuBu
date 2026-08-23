package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.ExpenseRecord
import com.kumo.bubu.domain.model.ExpenseRecordInput
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun observeRecentExpenseRecords(): Flow<List<ExpenseRecord>>
    suspend fun getExpenseRecord(id: Long): ExpenseRecord?
    suspend fun createExpenseRecord(input: ExpenseRecordInput): Long
    suspend fun updateExpenseRecord(id: Long, input: ExpenseRecordInput)
    suspend fun deleteExpenseRecord(id: Long)
}
