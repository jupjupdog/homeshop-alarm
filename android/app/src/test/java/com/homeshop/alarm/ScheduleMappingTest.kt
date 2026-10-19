package com.homeshop.alarm

import com.homeshop.alarm.collect.ScheduleCollector
import com.homeshop.alarm.collect.sources.GsShop
import com.homeshop.alarm.data.ScheduleRepository
import com.homeshop.alarm.ui.PriceSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 파서 결과가 DB 행(상품 단위)으로 제대로 펼쳐지는지 확인. */
class ScheduleMappingTest {
    private fun gsshop() = GsShop.parse(
        javaClass.classLoader!!.getResource("gsshop_20261019.html")!!.readText(), LocalDate.of(2026, 10, 19),
    ).also(ScheduleCollector::enrich)

    @Test
    fun mapsBroadcastsToFlatProducts() {
        val rows = ScheduleRepository.toEntities(gsshop())
        assertEquals(gsshop().sumOf { it.products.size }, rows.size)
        val washer = rows.first { it.name.startsWith("LG 통돌이 세탁기 19KG") }
        assertEquals("GS샵", washer.channel)
        assertEquals(790_000, washer.price)
        assertEquals("가전·디지털", washer.category)
        assertEquals("LG 통돌이 세탁기 19KG T19MX7A 미드", washer.searchQuery)
        // 2026-10-19 01:00 KST
        assertEquals(1_792_339_200_000L, washer.startMillis)
        assertTrue(washer.endMillis > washer.startMillis)
    }

    @Test
    fun priceSiteUrlsAreEncoded() {
        val url = PriceSite.NAVER.url("LG 통돌이 세탁기")
        assertTrue(url, url.contains("query=LG+%ED%86%B5"))
    }
}
