package com.homeshop.alarm.collect.sources

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ChannelSource
import com.homeshop.alarm.collect.Http
import com.homeshop.alarm.collect.KST
import com.homeshop.alarm.collect.Product
import com.homeshop.alarm.collect.digitsToInt
import com.homeshop.alarm.collect.get
import com.homeshop.alarm.collect.list
import com.homeshop.alarm.collect.parseJson
import com.homeshop.alarm.collect.squash
import com.homeshop.alarm.collect.str
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 롯데홈쇼핑 TV 편성표.
 * 출처: https://www.lotteimall.com/main/scheduleLive.lotte?bdDate=YYYYMMDD (JSON, 2026-10 확인). 원TV 는 scheduleOne.lotte.
 * - body.prod[] 1건 = 방송 1개: sdate+stime ~ edate+etime, 대표 상품 + related[](함께 방송하는 상품)
 * - relatedAdd(함께 사면 좋은 상품)는 방송 상품이 아니라 제외.
 * - 전날 마지막 방송이 첫 항목으로 같이 온다(수집기에서 중복 제거).
 */
object Lotte : ChannelSource {
    override val name = "롯데홈쇼핑"
    private const val BASE = "https://www.lotteimall.com"
    private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day))

    fun fetch(http: Http, day: LocalDate): String =
        http.get("$BASE/main/scheduleLive.lotte?bdDate=${day.format(YMD)}",
            mapOf("Referer" to "$BASE/main/tvschedule.lotte", "Accept" to "application/json"))

    fun parse(json: String, channel: String = name): List<Broadcast> =
        parseJson(json)["body"]["prod"].list.mapNotNull { p ->
            val start = dt(p["sdate"].str, p["stime"].str) ?: return@mapNotNull null
            val end = dt(p["edate"].str, p["etime"].str) ?: return@mapNotNull null
            val products = listOf(product(p, "time_img_url")) + p["related"].list.map { product(it, "img_url") }
            Broadcast(channel, start, end, products.first().name, products.toMutableList())
        }.sortedBy { it.start }

    private fun product(p: JsonElement, imageKey: String) = Product(
        id = p["goodsNo"].str.orEmpty(),
        name = p["name"].str.orEmpty().squash(),
        price = p["price_disc"].str.digitsToInt()?.takeIf { it > 0 },   // 보험·상담 상품은 0원
        image = p[imageKey].str ?: p["img_url"].str,
        url = p["linkInfo"].str?.let { BASE + it },
    )

    private fun dt(day: String?, hm: String?): OffsetDateTime? =
        if (day == null || hm == null) null else LocalDateTime.parse("$day $hm", DT).atOffset(KST)
}
