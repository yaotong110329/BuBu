package com.kumo.bubu.domain.repository

import com.kumo.bubu.domain.model.FuelPriceQuote
import com.kumo.bubu.domain.model.FuelProduct
import java.time.LocalDate

interface FuelPriceRepository {
    suspend fun getCpcManualPrice(product: FuelProduct, date: LocalDate): FuelPriceQuote?
}
