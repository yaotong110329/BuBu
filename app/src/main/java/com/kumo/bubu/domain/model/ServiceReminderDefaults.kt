package com.kumo.bubu.domain.model

/** Visible BuBu reference values; each vehicle may save an independent override. */
fun ServiceType.defaultReminderIntervalKm(): Long? = when (publicId) {
    "builtin-car-engine-oil", "builtin-car-oil-filter", "builtin-car-tire-rotation" -> 10_000L
    "builtin-car-air-filter" -> 40_000L
    "builtin-car-cabin-filter" -> 20_000L
    "builtin-car-brake-fluid" -> 40_000L
    "builtin-car-spark-plugs" -> 100_000L
    "builtin-car-transmission-oil" -> 60_000L
    "builtin-car-tires" -> 50_000L
    "builtin-car-coolant" -> 100_000L
    "builtin-car-brake-pads" -> 40_000L
    "builtin-car-engine-belt", "builtin-car-shocks" -> 80_000L
    "builtin-car-chassis" -> 50_000L
    "builtin-motorcycle-engine-oil" -> 1_000L
    "builtin-motorcycle-gear-oil", "builtin-motorcycle-air-filter", "builtin-motorcycle-drive-air-filter" -> 5_000L
    "builtin-motorcycle-spark-plug", "builtin-motorcycle-brake-pads", "builtin-motorcycle-front-tire", "builtin-motorcycle-drive-belt" -> 10_000L
    "builtin-motorcycle-rear-tire" -> 8_000L
    else -> null
}

fun ServiceType.defaultReminderIntervalMonths(): Long? = when (publicId) {
    "builtin-car-engine-oil", "builtin-car-oil-filter" -> 6L
    "builtin-car-air-filter", "builtin-car-brake-fluid" -> 24L
    "builtin-car-cabin-filter", "builtin-car-wipers" -> 12L
    "builtin-car-tires", "builtin-car-engine-belt", "builtin-car-coolant", "builtin-car-spark-plugs" -> 60L
    "builtin-car-transmission-oil" -> 48L
    "builtin-car-battery" -> 36L
    "builtin-motorcycle-engine-oil" -> 6L
    "builtin-motorcycle-gear-oil", "builtin-motorcycle-air-filter", "builtin-motorcycle-drive-air-filter", "builtin-motorcycle-spark-plug" -> 12L
    "builtin-motorcycle-brake-fluid" -> 24L
    "builtin-motorcycle-battery" -> 36L
    else -> null
}

fun ServiceType.defaultMaintenanceReminderType(): MaintenanceReminderType = when (publicId) {
    "builtin-car-tires", "builtin-car-battery", "builtin-car-wipers", "builtin-motorcycle-battery" ->
        MaintenanceReminderType.ESTIMATE
    "builtin-car-brake-pads", "builtin-car-engine-belt", "builtin-car-shocks", "builtin-car-chassis",
    "builtin-motorcycle-brake-pads", "builtin-motorcycle-front-tire", "builtin-motorcycle-rear-tire",
    "builtin-motorcycle-drive-belt" -> MaintenanceReminderType.INSPECT
    else -> MaintenanceReminderType.REPLACE
}
