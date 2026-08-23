package com.kumo.bubu.domain.model

import java.time.LocalDate
data class ExpenseRecord(val id: Long, val publicId: String, val vehicleId: Long, val dateEpochDay: Long, val timeMinuteOfDay: Int?, val sequenceInDay: Int, val category: ExpenseCategory, val totalCostTwd: Long, val note: String?, val createdAt: Long, val updatedAt: Long, val completedReminderId: Long? = null)
data class ExpenseRecordInput(val vehicleId: Long, val dateEpochDay: Long, val timeMinuteOfDay: Int?, val category: ExpenseCategory, val totalCostTwd: Long, val note: String? = null, val completeSameCycleReminder: Boolean = false)
enum class ExpenseCategory { LICENSE_TAX, ROAD_MAINTENANCE_FEE, INSURANCE, PARKING, TOLL, FINE, CAR_CARE, OTHER }
fun ExpenseRecordInput.validated(today: LocalDate = LocalDate.now()): ExpenseRecordInput { require(dateEpochDay <= today.toEpochDay()); require(timeMinuteOfDay == null || timeMinuteOfDay in 0 until 1440); require(totalCostTwd >= 0); return copy(note = note?.trim()?.takeIf(String::isNotEmpty)) }
