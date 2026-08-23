package com.kumo.bubu.data.repository

import com.kumo.bubu.data.local.dao.ReportDao
import com.kumo.bubu.data.local.dao.ReportOdometerRecordRow
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.domain.model.ReportCategoryTotal
import com.kumo.bubu.domain.model.ReportCostCategory
import com.kumo.bubu.domain.model.ReportData
import com.kumo.bubu.domain.model.ReportMonthCategoryTotal
import com.kumo.bubu.domain.model.ReportMonthTotal
import com.kumo.bubu.domain.model.ReportOdometerRecord
import com.kumo.bubu.domain.model.ReportQuery
import com.kumo.bubu.domain.model.ReportServiceMonthTotal
import com.kumo.bubu.domain.model.ReportSource
import com.kumo.bubu.domain.model.ReportVehicleCategoryTotal
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class OfflineReportRepository(
    private val reportDao: ReportDao,
) : ReportRepository {
    override fun observeReport(query: ReportQuery): Flow<ReportData> = combine(
        reportDao.observeCategoryTotals(query.vehicleIds, query.startEpochDay, query.endEpochDay),
        reportDao.observeMonthlyTotals(query.vehicleIds, query.startEpochDay, query.endEpochDay),
        reportDao.observeServiceMonthlyTotals(query.vehicleIds, query.startEpochDay, query.endEpochDay),
        reportDao.observeFuelRecordsForReport(query.vehicleIds, query.endEpochDay),
        reportDao.observeOdometerRecordsForReport(query.vehicleIds, query.endEpochDay),
    ) { categoryTotals, monthlyTotals, serviceMonthlyTotals, fuelRecords, odometerRecords ->
        ReportData(
            categoryTotals = categoryTotals.map { row ->
                ReportCategoryTotal(ReportCostCategory.valueOf(row.category), row.totalCostTwd)
            },
            monthlyTotals = monthlyTotals.map { row -> ReportMonthTotal(row.monthKey, row.totalCostTwd) },
            serviceMonthlyTotals = serviceMonthlyTotals.map { row ->
                ReportServiceMonthTotal(
                    monthKey = row.monthKey,
                    recordType = ServiceRecordType.valueOf(row.recordType),
                    totalCostTwd = row.totalCostTwd,
                )
            },
            fuelRecords = fuelRecords.map { it.toDomain() },
            odometerRecords = odometerRecords.map(::toReportOdometerRecord),
        )
    }.combine(
        reportDao.observeMonthlyCategoryTotals(query.vehicleIds, query.startEpochDay, query.endEpochDay),
    ) { data, monthlyCategoryTotals ->
        data.copy(
            monthlyCategoryTotals = monthlyCategoryTotals.map { row ->
                ReportMonthCategoryTotal(
                    monthKey = row.monthKey,
                    category = ReportCostCategory.valueOf(row.category),
                    totalCostTwd = row.totalCostTwd,
                )
            },
        )
    }.combine(
        reportDao.observeVehicleCategoryTotals(query.vehicleIds, query.startEpochDay, query.endEpochDay),
    ) { data, vehicleTotals ->
        data.copy(
            vehicleCategoryTotals = vehicleTotals.map { row ->
                ReportVehicleCategoryTotal(
                    vehicleId = row.vehicleId,
                    category = ReportCostCategory.valueOf(row.category),
                    totalCostTwd = row.totalCostTwd,
                )
            },
        )
    }

    private fun toReportOdometerRecord(row: ReportOdometerRecordRow): ReportOdometerRecord =
        ReportOdometerRecord(
            vehicleId = row.vehicleId,
            dateEpochDay = row.dateEpochDay,
            timeMinuteOfDay = row.timeMinuteOfDay,
            sequenceInDay = row.sequenceInDay,
            odometerKm = row.odometerKm,
            source = when (row.sourceType) {
                "FUEL" -> ReportSource.Fuel(row.sourceId)
                "SERVICE" -> ReportSource.Service(row.sourceId)
                else -> error("Unsupported report odometer source: ${row.sourceType}")
            },
        )
}
