package com.kumo.bubu.data.csv

import com.kumo.bubu.data.local.entity.ExpenseRecordEntity
import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.ServiceItemEntity
import com.kumo.bubu.data.local.entity.ServiceRecordEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.data.local.entity.VehicleReminderEntity
import com.kumo.bubu.domain.model.CsvExportRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CsvExportSource(
    val vehicles: List<VehicleEntity> = emptyList(),
    val fuelRecords: List<FuelRecordEntity> = emptyList(),
    val serviceRecords: List<ServiceRecordEntity> = emptyList(),
    val serviceItems: List<ServiceItemEntity> = emptyList(),
    val expenseRecords: List<ExpenseRecordEntity> = emptyList(),
    val reminders: List<VehicleReminderEntity> = emptyList(),
    val serviceAttachments: List<ServiceAttachmentEntity> = emptyList(),
)

object CsvExportTables {
    fun build(source: CsvExportSource, request: CsvExportRequest): List<CsvTable> {
        val vehicles = source.vehicles
            .filter { request.vehicleIds.isEmpty() || it.id in request.vehicleIds }
            .sortedWith(compareBy<VehicleEntity> { it.isArchived }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
        val vehicleReferences = vehicles.withIndex().associate { (index, vehicle) ->
            vehicle.id to "VEH-${(index + 1).toString().padStart(3, '0')}"
        }
        val selectedVehicleIds = vehicleReferences.keys
        val fuelRecords = source.fuelRecords
            .filter { it.vehicleId in selectedVehicleIds && it.dateEpochDay.inRequestRange(request) }
            .sortedWith(fuelRecordOrder())
        val serviceRecords = source.serviceRecords
            .filter { it.vehicleId in selectedVehicleIds && it.dateEpochDay.inRequestRange(request) }
            .sortedWith(serviceRecordOrder())
        val expenseRecords = source.expenseRecords
            .filter { it.vehicleId in selectedVehicleIds && it.dateEpochDay.inRequestRange(request) }
            .sortedWith(expenseRecordOrder())
        val fuelReferences = fuelRecords.withIndex().associate { (index, record) ->
            record.id to "FUEL-${(index + 1).toString().padStart(6, '0')}"
        }
        val serviceReferences = serviceRecords.withIndex().associate { (index, record) ->
            record.id to "SRV-${(index + 1).toString().padStart(6, '0')}"
        }
        val expenseReferences = expenseRecords.withIndex().associate { (index, record) ->
            record.id to "EXP-${(index + 1).toString().padStart(6, '0')}"
        }
        val serviceItems = source.serviceItems
            .filter { it.serviceRecordId in serviceReferences }
            .sortedWith(compareBy<ServiceItemEntity> { serviceReferences.getValue(it.serviceRecordId) }
                .thenBy(ServiceItemEntity::sequenceInRecord)
                .thenBy(ServiceItemEntity::id))
        val itemReferences = serviceItems.withIndex().associate { (index, item) ->
            item.id to "SIT-${(index + 1).toString().padStart(6, '0')}"
        }
        val reminders = source.reminders
            .filter { it.vehicleId in selectedVehicleIds }
            .sortedWith(compareBy<VehicleReminderEntity> { vehicleReferences.getValue(it.vehicleId) }
                .thenBy { it.dueDateEpochDay ?: Long.MAX_VALUE }
                .thenBy { it.dueOdometerKm ?: Long.MAX_VALUE }
                .thenBy(VehicleReminderEntity::id))
        val reminderReferences = reminders.withIndex().associate { (index, reminder) ->
            reminder.id to "REM-${(index + 1).toString().padStart(6, '0')}"
        }
        val attachments = source.serviceAttachments
            .filter { it.serviceRecordId in serviceReferences }
            .sortedWith(compareBy<ServiceAttachmentEntity> { serviceReferences.getValue(it.serviceRecordId) }
                .thenBy(ServiceAttachmentEntity::sequenceInRecord)
                .thenBy(ServiceAttachmentEntity::id))

        return listOf(
            CsvTable(VEHICLES_FILE, VEHICLE_HEADERS, vehicles.map { vehicle ->
                listOf(
                    vehicleReferences.getValue(vehicle.id), vehicle.name, vehicle.vehicleType.name,
                    vehicle.motorcycleClass?.name.orEmpty(), vehicle.brand.orEmpty(), vehicle.model.orEmpty(),
                    vehicle.manufactureYear?.toString().orEmpty(), vehicle.engineDisplacementCc?.toString().orEmpty(),
                    vehicle.licensePlate.orEmpty(), vehicle.powertrainType?.name.orEmpty(),
                    vehicle.trackingStartDateEpochDay.toLocalDate(), vehicle.trackingStartOdometerKm.toString(),
                    vehicle.currentOdometerKm.toString(), vehicle.note.orEmpty(), vehicle.isArchived.toString(),
                    vehicle.primaryInspectionMonthDay?.toString().orEmpty(), vehicle.secondaryInspectionMonthDay?.toString().orEmpty(),
                )
            }),
            CsvTable(FUEL_FILE, FUEL_HEADERS, fuelRecords.map { record ->
                listOf(
                    fuelReferences.getValue(record.id), vehicleReferences.getValue(record.vehicleId),
                    record.dateEpochDay.toLocalDate(), record.timeMinuteOfDay.toLocalTime(), record.sequenceInDay.toString(),
                    record.odometerKm.toString(), record.fuelVolumeMl.milliToDecimal(),
                    record.pricePerLiterMilli?.milliToDecimal().orEmpty(), record.totalCostTwd.toString(),
                    record.isFullTank.toString(), record.fuelProduct?.name.orEmpty(), record.fuelingMode.name, record.note.orEmpty(),
                )
            }),
            CsvTable(SERVICE_FILE, SERVICE_HEADERS, serviceRecords.map { record ->
                listOf(
                    serviceReferences.getValue(record.id), vehicleReferences.getValue(record.vehicleId),
                    record.dateEpochDay.toLocalDate(), record.timeMinuteOfDay.toLocalTime(), record.sequenceInDay.toString(),
                    record.odometerKm.toString(), record.recordType.name, record.title, record.paymentMethod?.name.orEmpty(),
                    record.totalCostTwd.toString(), record.note.orEmpty(),
                )
            }),
            CsvTable(SERVICE_ITEMS_FILE, SERVICE_ITEM_HEADERS, serviceItems.map { item ->
                listOf(
                    itemReferences.getValue(item.id), serviceReferences.getValue(item.serviceRecordId),
                    item.sequenceInRecord.toString(), item.nameSnapshot, item.quantityMilli.milliToDecimal(),
                    item.quantityUnit.name, item.unitPriceTwd.toString(), item.subtotalTwd.toString(),
                    item.nextDueOdometerKm?.toString().orEmpty(), item.nextDueDateEpochDay?.toLocalDate().orEmpty(),
                    item.note.orEmpty(),
                )
            }),
            CsvTable(EXPENSE_FILE, EXPENSE_HEADERS, expenseRecords.map { record ->
                listOf(
                    expenseReferences.getValue(record.id), vehicleReferences.getValue(record.vehicleId),
                    record.dateEpochDay.toLocalDate(), record.timeMinuteOfDay.toLocalTime(), record.sequenceInDay.toString(),
                    record.category.name, record.totalCostTwd.toString(), record.note.orEmpty(),
                )
            }),
            CsvTable(ODOMETER_CORRECTIONS_FILE, ODOMETER_CORRECTION_HEADERS, emptyList()),
            CsvTable(REMINDERS_FILE, REMINDER_HEADERS, reminders.map { reminder ->
                listOf(
                    reminderReferences.getValue(reminder.id), vehicleReferences.getValue(reminder.vehicleId),
                    reminder.source.name, reminder.sourceServiceItemId?.let(itemReferences::get).orEmpty(), reminder.title,
                    reminder.dueOdometerKm?.toString().orEmpty(), reminder.dueDateEpochDay?.toLocalDate().orEmpty(),
                    reminder.referenceDateEpochDay?.toLocalDate().orEmpty(),
                    reminder.completedByServiceRecordId?.let(serviceReferences::get).orEmpty(),
                    reminder.completedByExpenseRecordId?.let(expenseReferences::get).orEmpty(),
                    reminder.completedAt?.toLocalTimestamp().orEmpty(), reminder.snoozedUntilEpochDay?.toLocalDate().orEmpty(),
                    reminder.isEnabled.toString(), reminder.lastNotifiedStatus?.name.orEmpty(),
                    reminder.estimatedNotificationEpochDay?.toLocalDate().orEmpty(),
                )
            }),
            CsvTable(ATTACHMENTS_FILE, ATTACHMENT_HEADERS, attachments.mapIndexed { index, attachment ->
                listOf(
                    "ATT-${(index + 1).toString().padStart(6, '0')}",
                    serviceReferences.getValue(attachment.serviceRecordId), attachment.displayName,
                    "service_attachment", attachment.mimeType.orEmpty(), "", attachment.createdAt.toLocalTimestamp(),
                )
            }),
        )
    }

    fun readme(): String = """
        BuBu CSV 匯出說明
        
        此 ZIP 僅供試算表閱讀與分析，不能用於反向匯入或還原 BuBu 資料。
        日期使用 YYYY-MM-DD；時間使用本地 HH:mm，未知時間留白。金額為新臺幣整數；公升與每公升單價最多三位小數。
        匯出參照僅在本 ZIP 有效。附件清單不包含附件實體檔案，也不包含私有路徑或 Android URI。
        odometer_corrections.csv 目前僅提供固定欄位，因 BuBu 尚未提供獨立里程錯誤修正資料。
    """.trimIndent()

    private fun fuelRecordOrder(): Comparator<FuelRecordEntity> =
        compareBy<FuelRecordEntity> { it.vehicleId }
            .thenBy(FuelRecordEntity::dateEpochDay)
            .thenBy { it.timeMinuteOfDay ?: -1 }
            .thenBy(FuelRecordEntity::sequenceInDay)
            .thenBy(FuelRecordEntity::id)

    private fun serviceRecordOrder(): Comparator<ServiceRecordEntity> =
        compareBy<ServiceRecordEntity> { it.vehicleId }
            .thenBy(ServiceRecordEntity::dateEpochDay)
            .thenBy { it.timeMinuteOfDay ?: -1 }
            .thenBy(ServiceRecordEntity::sequenceInDay)
            .thenBy(ServiceRecordEntity::id)

    private fun expenseRecordOrder(): Comparator<ExpenseRecordEntity> =
        compareBy<ExpenseRecordEntity> { it.vehicleId }
            .thenBy(ExpenseRecordEntity::dateEpochDay)
            .thenBy { it.timeMinuteOfDay ?: -1 }
            .thenBy(ExpenseRecordEntity::sequenceInDay)
            .thenBy(ExpenseRecordEntity::id)

    private fun Long.inRequestRange(request: CsvExportRequest): Boolean =
        (request.startEpochDay == null || this >= request.startEpochDay) &&
            (request.endEpochDay == null || this <= request.endEpochDay)

    private fun Long.toLocalDate(): String = LocalDate.ofEpochDay(this).toString()

    private fun Int?.toLocalTime(): String = this?.let { minute ->
        "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)
    }.orEmpty()

    private fun Long.milliToDecimal(): String = BigDecimal.valueOf(this, 3).stripTrailingZeros().toPlainString()

    private fun Long.toLocalTimestamp(): String = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(TIMESTAMP_FORMAT)

    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private const val VEHICLES_FILE = "vehicles.csv"
    private const val FUEL_FILE = "fuel_records.csv"
    private const val SERVICE_FILE = "service_records.csv"
    private const val SERVICE_ITEMS_FILE = "service_items.csv"
    private const val EXPENSE_FILE = "expense_records.csv"
    private const val ODOMETER_CORRECTIONS_FILE = "odometer_corrections.csv"
    private const val REMINDERS_FILE = "reminders.csv"
    private const val ATTACHMENTS_FILE = "attachments.csv"

    private val VEHICLE_HEADERS = listOf(
        "vehicle_ref", "name", "vehicle_type", "motorcycle_class", "brand", "model", "manufacture_year",
        "engine_displacement_cc", "license_plate", "powertrain_type", "tracking_start_date",
        "tracking_start_odometer_km", "current_odometer_km", "note", "is_archived",
        "primary_inspection_month_day", "secondary_inspection_month_day",
    )
    private val FUEL_HEADERS = listOf(
        "fuel_ref", "vehicle_ref", "date", "time", "sequence", "odometer_km", "volume_l",
        "price_per_liter_twd", "total_cost_twd", "is_full_tank", "fuel_product", "fueling_mode", "note",
    )
    private val SERVICE_HEADERS = listOf(
        "service_ref", "vehicle_ref", "date", "time", "sequence", "odometer_km", "record_type", "title",
        "payment_method", "total_cost_twd", "note",
    )
    private val SERVICE_ITEM_HEADERS = listOf(
        "service_item_ref", "service_ref", "sequence", "name", "quantity", "quantity_unit", "unit_price_twd",
        "subtotal_twd", "next_due_odometer_km", "next_due_date", "note",
    )
    private val EXPENSE_HEADERS = listOf(
        "expense_ref", "vehicle_ref", "date", "time", "sequence", "category", "total_cost_twd", "note",
    )
    private val ODOMETER_CORRECTION_HEADERS = listOf(
        "correction_ref", "record_ref", "original_odometer_km", "corrected_odometer_km", "reason", "corrected_at",
    )
    private val REMINDER_HEADERS = listOf(
        "reminder_ref", "vehicle_ref", "source", "service_item_ref", "title", "due_odometer_km", "due_date",
        "reference_date", "completed_by_service_ref", "completed_by_expense_ref", "completed_at", "snoozed_until_date",
        "is_enabled", "last_notified_status", "estimated_notification_date",
    )
    private val ATTACHMENT_HEADERS = listOf(
        "attachment_ref", "service_ref", "display_name", "role", "mime_type", "note", "created_at",
    )
}
