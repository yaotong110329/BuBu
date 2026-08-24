package com.kumo.bubu.domain.model

/** Conservative built-in intervals; custom types intentionally have no assumed interval. */
fun ServiceType.defaultReminderIntervalKm(): Long? = when (publicId) {
    "builtin-car-engine-oil", "builtin-car-oil-filter" -> 5_000L
    "builtin-car-air-filter", "builtin-car-cabin-filter" -> 20_000L
    "builtin-car-brake-fluid" -> 40_000L
    "builtin-car-spark-plugs" -> 100_000L
    "builtin-car-transmission-oil" -> 60_000L
    "builtin-car-tires" -> 50_000L
    "builtin-car-coolant" -> 80_000L
    "builtin-car-brake-pads" -> 40_000L
    "builtin-car-battery" -> 60_000L
    "builtin-motorcycle-engine-oil" -> 1_000L
    "builtin-motorcycle-oil-filter" -> 3_000L
    "builtin-motorcycle-air-filter" -> 6_000L
    "builtin-motorcycle-spark-plug" -> 8_000L
    "builtin-motorcycle-tires" -> 15_000L
    "builtin-motorcycle-brake-fluid" -> 20_000L
    "builtin-motorcycle-battery" -> 30_000L
    else -> null
}
