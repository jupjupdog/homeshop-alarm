package com.homeshop.alarm.collect.sources

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ChannelSource
import com.homeshop.alarm.collect.Http
import com.homeshop.alarm.collect.KST
import com.homeshop.alarm.collect.Product
import com.homeshop.alarm.collect.get
import com.homeshop.alarm.collect.int
import com.homeshop.alarm.collect.list
import com.homeshop.alarm.collect.parseJson
import com.homeshop.alarm.collect.squash
import com.homeshop.alarm.collect.str
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * NS홈쇼핑 TV 편성표.
 * 출처: https://mapi.nsmall.com/md/api/v1/display/media/schedule/tv/YYYYMMDD (모바일 편성표 JSON, 2026-10 확인)
 * - tv 대신 shopplus 는 NS샵+(T커머스), all 은 둘 다.
 * - data.resultData.totalOrgan[] 1건 = 방송 1개: startDtm/endDtm, pgmNm, goods(대표), relTotalOrgan[](함께 방송)
 * - 다음날 첫 방송이 마지막 항목으로 같이 온다(수집기에서 중복 제거).
 */
object Nsmall : ChannelSource {
    override val name = "NS홈쇼핑"

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day))

    fun fetch(http: Http, day: LocalDate, kind: String = "tv"): String =
        http.get("https://mapi.nsmall.com/md/api/v1/display/media/schedule/$kind/${day.format(YMD)}",
            mapOf("Origin" to "https://m.nsmall.com", "Referer" to "https://m.nsmall.com/", "Accept" to "application/json"))

    fun parse(json: String, channel: String = name): List<Broadcast> =
        parseJson(json)["data"]["resultData"]["totalOrgan"].list.mapNotNull { x ->
            val start = x["startDtm"].str ?: return@mapNotNull null
            val end = x["endDtm"].str ?: return@mapNotNull null
            val code = x["goodsCd"].str ?: return@mapNotNull null
            if (x["goods"] == null) return@mapNotNull null
            val products = mutableListOf(product(code, x["goods"]))
            for (r in x["relTotalOrgan"].list) {
                val rc = r["goodsCd"].str ?: continue
                if (rc != code) products += product(rc, r["goods"])
            }
            Broadcast(channel, dt(start), dt(end), x["pgmNm"].str ?: products.first().name, products)
        }.sortedBy { it.start }

    private fun product(code: String, goods: JsonElement?) = Product(
        id = code,
        name = goods["productNm"].str.orEmpty().squash(),
        price = goods["applyPrice"].int?.takeIf { it > 0 } ?: goods["salePrice"].int,
        image = "https://product-image.prod-nsmall.com/new/${code}_F.jpg",
        url = "https://m.nsmall.com/goods/$code",
    )

    private fun dt(s: String) = LocalDateTime.parse(s, YMDHMS).atOffset(KST)
}
