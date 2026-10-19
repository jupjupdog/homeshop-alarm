package com.homeshop.alarm

import com.homeshop.alarm.data.ScheduleRepository
import com.homeshop.alarm.data.remote.ScheduleDto
import com.homeshop.alarm.model.Categories
import com.homeshop.alarm.ui.PriceSite
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 수집기가 실제 편성표로 만든 sample_schedule.json 을 앱이 제대로 읽는지 확인. */
class ScheduleMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun sample(): ScheduleDto {
        val text = javaClass.classLoader!!.getResource("sample_schedule.json")!!.readText()
        return json.decodeFromString(ScheduleDto.serializer(), text)
    }

    @Test
    fun mapsBroadcastsToFlatProducts() {
        val rows = ScheduleRepository.toEntities(sample())
        assertEquals(9, rows.size)
        val washer = rows.first { it.name.startsWith("LG 통돌이 세탁기 19KG") }
        assertEquals("GS샵", washer.channel)
        assertEquals(790_000, washer.price)
        assertEquals("가전·디지털", washer.category)
        // 2026-10-19 01:00 KST
        assertEquals(1_792_339_200_000L, washer.startMillis)
        assertTrue(washer.endMillis > washer.startMillis)
    }

    @Test
    fun collectorCategoriesMatchApp() {
        assertEquals(Categories.ALL.toSet(), sample().categories.toSet())
    }

    @Test
    fun priceSiteUrlsAreEncoded() {
        val url = PriceSite.NAVER.url("LG 통돌이 세탁기")
        assertTrue(url, url.contains("query=LG+%ED%86%B5"))
    }
}
