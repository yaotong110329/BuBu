package com.kumo.bubu.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

data class ReportCategoryTotalRow(
    val category: String,
    val totalCostTwd: Long,
)

data class ReportMonthTotalRow(
    val monthKey: String,
    val totalCostTwd: Long,
)

data class ReportMonthCategoryTotalRow(
    val monthKey: String,
    val category: String,
    val totalCostTwd: Long,
)

data class ReportVehicleCategoryTotalRow(
    val vehicleId: Long,
    val category: String,
    val totalCostTwd: Long,
)

data class ReportServiceMonthTotalRow(
    val monthKey: String,
    val recordType: String,
    val totalCostTwd: Long,
)

data class ReportOdometerRecordRow(
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val sourceType: String,
    val sourceId: Long,
)

@Dao
interface ReportDao {
    @Query(
        """
        SELECT category, SUM(totalCostTwd) AS totalCostTwd
        FROM (
            SELECT 'FUEL' AS category, totalCostTwd
            FROM fuel_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT CASE recordType
                WHEN 'MAINTENANCE' THEN 'MAINTENANCE'
                WHEN 'REPAIR' THEN 'REPAIR'
                ELSE 'OTHER'
            END AS category, totalCostTwd
            FROM service_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT CASE category
                WHEN 'LICENSE_TAX' THEN 'LICENSE_TAX'
                WHEN 'ROAD_MAINTENANCE_FEE' THEN 'ROAD_MAINTENANCE_FEE'
                WHEN 'INSURANCE' THEN 'INSURANCE'
                ELSE 'OTHER'
            END AS category, totalCostTwd
            FROM expense_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        )
        GROUP BY category
        ORDER BY category
        """,
    )
    fun observeCategoryTotals(
        vehicleIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReportCategoryTotalRow>>

    @Query(
        """
        SELECT vehicleId, category, SUM(totalCostTwd) AS totalCostTwd
        FROM (
            SELECT vehicleId, 'FUEL' AS category, totalCostTwd
            FROM fuel_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT vehicleId, CASE recordType
                WHEN 'MAINTENANCE' THEN 'MAINTENANCE'
                WHEN 'REPAIR' THEN 'REPAIR'
                ELSE 'OTHER'
            END AS category, totalCostTwd
            FROM service_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT vehicleId, CASE category
                WHEN 'LICENSE_TAX' THEN 'LICENSE_TAX'
                WHEN 'ROAD_MAINTENANCE_FEE' THEN 'ROAD_MAINTENANCE_FEE'
                WHEN 'INSURANCE' THEN 'INSURANCE'
                ELSE 'OTHER'
            END AS category, totalCostTwd
            FROM expense_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        )
        GROUP BY vehicleId, category
        ORDER BY vehicleId, category
        """,
    )
    fun observeVehicleCategoryTotals(
        vehicleIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReportVehicleCategoryTotalRow>>

    @Query(
        """
        SELECT strftime('%Y-%m', date('1970-01-01', dateEpochDay || ' days')) AS monthKey,
               SUM(totalCostTwd) AS totalCostTwd
        FROM (
            SELECT dateEpochDay, totalCostTwd FROM fuel_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT dateEpochDay, totalCostTwd FROM service_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT dateEpochDay, totalCostTwd FROM expense_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        )
        GROUP BY monthKey
        ORDER BY monthKey
        """,
    )
    fun observeMonthlyTotals(
        vehicleIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReportMonthTotalRow>>

    @Query(
        """
        SELECT strftime('%Y-%m', date('1970-01-01', dateEpochDay || ' days')) AS monthKey,
               category,
               SUM(totalCostTwd) AS totalCostTwd
        FROM (
            SELECT dateEpochDay, 'FUEL' AS category, totalCostTwd FROM fuel_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT dateEpochDay, CASE recordType
                WHEN 'MAINTENANCE' THEN 'MAINTENANCE'
                WHEN 'REPAIR' THEN 'REPAIR'
                ELSE 'OTHER'
            END AS category, totalCostTwd FROM service_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
            UNION ALL
            SELECT dateEpochDay, CASE category
                WHEN 'LICENSE_TAX' THEN 'LICENSE_TAX'
                WHEN 'ROAD_MAINTENANCE_FEE' THEN 'ROAD_MAINTENANCE_FEE'
                WHEN 'INSURANCE' THEN 'INSURANCE'
                ELSE 'OTHER'
            END AS category, totalCostTwd FROM expense_records
            WHERE vehicleId IN (:vehicleIds) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        )
        GROUP BY monthKey, category
        ORDER BY monthKey, category
        """,
    )
    fun observeMonthlyCategoryTotals(
        vehicleIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReportMonthCategoryTotalRow>>

    @Query(
        """
        SELECT strftime('%Y-%m', date('1970-01-01', dateEpochDay || ' days')) AS monthKey,
               recordType,
               SUM(totalCostTwd) AS totalCostTwd
        FROM service_records
        WHERE vehicleId IN (:vehicleIds)
          AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
          AND recordType IN ('MAINTENANCE', 'REPAIR')
        GROUP BY monthKey, recordType
        ORDER BY monthKey, recordType
        """,
    )
    fun observeServiceMonthlyTotals(
        vehicleIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReportServiceMonthTotalRow>>

    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId IN (:vehicleIds) AND dateEpochDay <= :endEpochDay
        ORDER BY vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, id
        """,
    )
    fun observeFuelRecordsForReport(
        vehicleIds: List<Long>,
        endEpochDay: Long,
    ): Flow<List<FuelRecordEntity>>

    @Query(
        """
        SELECT vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, odometerKm,
               'FUEL' AS sourceType, id AS sourceId
        FROM fuel_records
        WHERE vehicleId IN (:vehicleIds) AND dateEpochDay <= :endEpochDay
        UNION ALL
        SELECT vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, odometerKm,
               'SERVICE' AS sourceType, id AS sourceId
        FROM service_records
        WHERE vehicleId IN (:vehicleIds) AND dateEpochDay <= :endEpochDay
        ORDER BY vehicleId, dateEpochDay, timeMinuteOfDay, sequenceInDay, sourceId
        """,
    )
    fun observeOdometerRecordsForReport(
        vehicleIds: List<Long>,
        endEpochDay: Long,
    ): Flow<List<ReportOdometerRecordRow>>
}
