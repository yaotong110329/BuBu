package com.kumo.bubu.domain.model

data class FuelPriceQuote(
    val product: FuelProduct,
    val pricePerLiterMilli: Long,
    val effectiveDateEpochDay: Long,
)
