package com.kumo.bubu.data.repository

import com.kumo.bubu.domain.model.FuelPriceQuote
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.repository.FuelPriceRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.io.IOException
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CpcFuelPriceRepository : FuelPriceRepository {
    override suspend fun getCpcManualPrice(product: FuelProduct, date: LocalDate): FuelPriceQuote? =
        product.cpcProductId()?.let { productId ->
            val prices = historyMutex.withLock {
                historyByProduct[product] ?: withContext(Dispatchers.IO) {
                    fetchHistoricalPrices(productId)
                }
                    .also { prices -> if (prices.isNotEmpty()) historyByProduct[product] = prices }
            }
            prices.latestOnOrBefore(date)?.let { (effectiveDate, price) ->
                FuelPriceQuote(product, price, effectiveDate.toEpochDay())
            }
        }

    private val historyByProduct = mutableMapOf<FuelProduct, List<Pair<LocalDate, Long>>>()
    private val historyMutex = Mutex()

    private companion object {
        const val HISTORICAL_PRICE_URL = "https://vipmbr.cpc.com.tw/CPCSTN/ListPriceWebService.asmx/getCPCMainProdListPrice_Historical"
        const val TIMEOUT_MILLIS = 15_000
        const val RETRY_DELAY_MILLIS = 300L
    }

    private fun fetchHistoricalPrices(productId: Int): List<Pair<LocalDate, Long>> {
        var failure: IOException? = null
        repeat(2) { attempt ->
            try {
                return requestHistoricalPrices(productId)
            } catch (error: IOException) {
                failure = error
                if (attempt == 0) Thread.sleep(RETRY_DELAY_MILLIS)
            }
        }
        throw requireNotNull(failure)
    }

    private fun requestHistoricalPrices(productId: Int): List<Pair<LocalDate, Long>> {
        val connection = (URL("$HISTORICAL_PRICE_URL?prodid=$productId").openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/xml, text/xml, */*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "BuBu/1.0 (Android)")
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) throw IOException("CPC historical price request failed with HTTP $statusCode")
            return parseHistoricalPrices(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseHistoricalPrice(xml: String, product: FuelProduct, date: LocalDate): FuelPriceQuote? {
    return parseHistoricalPrices(xml).latestOnOrBefore(date)
        ?.let { (effectiveDate, price) -> FuelPriceQuote(product, price, effectiveDate.toEpochDay()) }
}

private fun parseHistoricalPrices(xml: String): List<Pair<LocalDate, Long>> {
    return CPC_ROW_PATTERN.findAll(xml).mapNotNull { row ->
        val date = row.value.cpcFieldValue("牌價生效時間")?.toCpcDateOrNull()
        val price = row.value.cpcFieldValue("參考牌價")?.toCpcMilliTwdOrNull()
        if (date == null || price == null) null else date to price
    }.toList()
}

private fun String.cpcFieldValue(fieldName: String): String? =
    Regex("<$fieldName(?:\\s[^>]*)?>(.*?)</$fieldName>", setOf(RegexOption.DOT_MATCHES_ALL))
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.trim()

private fun List<Pair<LocalDate, Long>>.latestOnOrBefore(date: LocalDate): Pair<LocalDate, Long>? =
    filter { (effectiveDate, _) -> effectiveDate <= date }
        .maxByOrNull { it.first }

private fun String.toCpcDateOrNull(): LocalDate? = listOf("yyyy/MM/dd", "yyyy-MM-dd").firstNotNullOfOrNull { pattern ->
    runCatching { LocalDate.parse(trim().take(10), DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
}

private fun String.toCpcMilliTwdOrNull(): Long? = runCatching {
    BigDecimal(trim().replace("元/公升", "").replace("元", "").replace(",", ""))
        .movePointRight(3)
        .setScale(0, RoundingMode.UNNECESSARY)
        .longValueExact()
}.getOrNull()

private fun FuelProduct.cpcProductId(): Int? = when (this) {
    FuelProduct.GASOLINE_92 -> 1
    FuelProduct.GASOLINE_95 -> 2
    FuelProduct.GASOLINE_98 -> 3
    FuelProduct.DIESEL -> 4
    FuelProduct.OTHER -> null
}

private val CPC_ROW_PATTERN = Regex("<tbTable(?:\\s[^>]*)?>(.*?)</tbTable>", RegexOption.DOT_MATCHES_ALL)
