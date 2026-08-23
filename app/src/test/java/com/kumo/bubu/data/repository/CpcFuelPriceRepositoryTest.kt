package com.kumo.bubu.data.repository

import com.kumo.bubu.domain.model.FuelProduct
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpcFuelPriceRepositoryTest {
    @Test
    fun parsesCpcXmlRowsThatUseChineseElementNames() {
        val quote = parseHistoricalPrice(
            xml = """
                <DataSet xmlns="http://tmtd.cpc.com.tw/">
                    <diffgr:diffgram xmlns:diffgr="urn:schemas-microsoft-com:xml-diffgram-v1">
                        <NewDataSet xmlns="">
                            <tbTable diffgr:id="tbTable934">
                                <牌價生效時間>2026-08-10T00:00:00+08:00</牌價生效時間>
                                <產品名>無鉛汽油95</產品名>
                                <參考牌價>32</參考牌價>
                                <計價單位>元/公升</計價單位>
                            </tbTable>
                        </NewDataSet>
                    </diffgr:diffgram>
                </DataSet>
            """.trimIndent(),
            product = FuelProduct.GASOLINE_95,
            date = LocalDate.of(2026, 8, 12),
        )

        assertEquals(32_000L, quote?.pricePerLiterMilli)
        assertEquals(LocalDate.of(2026, 8, 10).toEpochDay(), quote?.effectiveDateEpochDay)
    }

    @Test
    fun selectsTheLatestEffectiveHistoricalManualPriceOnOrBeforeFuelDate() {
        val quote = parseHistoricalPrice(
            xml = """
                <NewDataSet>
                    <tbTable><牌價生效時間>2026-08-03T00:00:00+08:00</牌價生效時間><參考牌價>29.1</參考牌價></tbTable>
                    <tbTable><牌價生效時間>2026-07-27T00:00:00+08:00</牌價生效時間><參考牌價>28.7</參考牌價></tbTable>
                </NewDataSet>
            """.trimIndent(),
            product = FuelProduct.GASOLINE_95,
            date = LocalDate.of(2026, 8, 5),
        )

        assertEquals(29_100L, quote?.pricePerLiterMilli)
        assertEquals(LocalDate.of(2026, 8, 3).toEpochDay(), quote?.effectiveDateEpochDay)
    }

    @Test
    fun returnsNoPriceWhenFuelDateIsBeforeTheFirstHistoricalPrice() {
        val quote = parseHistoricalPrice(
            xml = "<NewDataSet><tbTable><牌價生效時間>2026-08-03T00:00:00+08:00</牌價生效時間><參考牌價>29.1</參考牌價></tbTable></NewDataSet>",
            product = FuelProduct.GASOLINE_95,
            date = LocalDate.of(2026, 8, 2),
        )

        assertNull(quote)
    }
}
