package com.homeshop.alarm.collect.sources

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ChannelSource
import com.homeshop.alarm.collect.Http
import com.homeshop.alarm.collect.KST
import com.homeshop.alarm.collect.Product
import com.homeshop.alarm.collect.digitsToInt
import com.homeshop.alarm.collect.squash
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * GS샵 TV 편성표.
 * 출처: https://www.gsshop.com/shop/tv/tvScheduleDetail.gs?today=YYYYMMDD (편성표 페이지가 부르는 HTML 조각, 2026-10 확인)
 * - 방송 = article.items, 상품 = li.prd-item. 상품 링크의 brddate=YYYYMMDDHHMMSS 가 정확한 방송 시작 시각.
 * - 종료 시각이 없어서 다음 방송 시작을 종료로 쓴다.
 */
object GsShop : ChannelSource {
    override val name = "GS샵"
    private const val BASE = "https://www.gsshop.com"

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day), day)

    fun fetch(http: Http, day: LocalDate): String =
        http.get("$BASE/shop/tv/tvScheduleDetail.gs?today=${day.format(YMD)}",
            mapOf("Referer" to "$BASE/shop/tv/tvScheduleMain.gs"))

    fun parse(html: String, day: LocalDate): List<Broadcast> {
        val doc = Jsoup.parse(html, BASE)
        val raw = mutableListOf<Pair<OffsetDateTime, List<Product>>>()
        for (art in doc.select("article.items")) {
            var start: OffsetDateTime? = null
            val products = art.select("li.prd-item").mapNotNull { li ->
                val a = li.selectFirst("dt.prd-name a") ?: return@mapNotNull null
                a.select("span").remove()                       // "[TV상품]" 같은 라벨
                val href = a.attr("href")
                start = start ?: startFromLink(href)
                Product(
                    id = li.id().removePrefix("li_"),
                    name = a.text().squash(),
                    price = li.selectFirst("dd.prd-price .set-price strong")?.text().digitsToInt(),
                    image = li.selectFirst("a.prd-img img")?.absUrl("src")?.ifEmpty { null },  // "//static..." → https:
                    url = href.ifEmpty { null }?.let { a.absUrl("href") },
                )
            }
            val s = start ?: art.selectFirst("aside .times")?.text()?.trim()?.split(":")?.let { (h, m) ->
                day.atTime(h.toInt(), m.toInt()).atOffset(KST)
            } ?: continue
            if (products.isNotEmpty()) raw += s to products
        }
        raw.sortBy { it.first }
        return raw.mapIndexed { i, (start, products) ->
            val end = raw.getOrNull(i + 1)?.first ?: start.plusMinutes(60)
            Broadcast(name, start, end, products.first().name, products.toMutableList())
        }
    }

    private fun startFromLink(href: String): OffsetDateTime? {
        val brd = Regex("[?&]brddate=(\\d{12})").find(href)?.groupValues?.get(1) ?: return null
        return LocalDateTime.parse(brd, YMDHM).atOffset(KST)
    }
}

internal val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
internal val YMDHM: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
internal val YMDHMS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
