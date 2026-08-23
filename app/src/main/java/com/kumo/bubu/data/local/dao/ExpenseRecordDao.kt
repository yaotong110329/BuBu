package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kumo.bubu.data.local.entity.ExpenseRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseRecordDao {
    @Query(
        "SELECT * FROM expense_records " +
            "ORDER BY vehicleId ASC, dateEpochDay ASC, timeMinuteOfDay ASC, sequenceInDay ASC, id ASC",
    )
    suspend fun getAllForExport(): List<ExpenseRecordEntity>

    @Query("SELECT * FROM expense_records ORDER BY dateEpochDay DESC, timeMinuteOfDay DESC, sequenceInDay DESC, id DESC")
    fun observeRecent(): Flow<List<ExpenseRecordEntity>>

    @Query("SELECT * FROM expense_records WHERE id = :id")
    suspend fun getById(id: Long): ExpenseRecordEntity?

    @Query("SELECT COALESCE(MAX(sequenceInDay), -1) + 1 FROM expense_records WHERE vehicleId = :vehicleId AND dateEpochDay = :dateEpochDay")
    suspend fun nextSequenceInDay(vehicleId: Long, dateEpochDay: Long): Int

    @Insert
    suspend fun insert(record: ExpenseRecordEntity): Long

    @Update
    suspend fun update(record: ExpenseRecordEntity)

    @Query("DELETE FROM expense_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
