package com.kumo.bubu.feature.fuel

import com.kumo.bubu.domain.model.FuelPriceQuote
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.repository.FuelPriceRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred

class FakeFuelPriceRepository : FuelPriceRepository {
    var quote: FuelPriceQuote? = null
    var pendingQuote: CompletableDeferred<FuelPriceQuote?>? = null
    var failure: Throwable? = null
    var requestedProducts = mutableListOf<FuelProduct>()

    override suspend fun getCpcManualPrice(product: FuelProduct, date: LocalDate): FuelPriceQuote? {
        requestedProducts += product
        failure?.let { throw it }
        return pendingQuote?.await() ?: quote
    }
}
