package com.homeshop.alarm.collect.sources

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ChannelSource
import com.homeshop.alarm.collect.Http
import com.homeshop.alarm.collect.KST
import com.homeshop.alarm.collect.Product
import com.homeshop.alarm.collect.get
import com.homeshop.alarm.collect.int
import com.homeshop.alarm.collect.list
import com.homeshop.alarm.collect.long
import com.homeshop.alarm.collect.parseJson
import com.homeshop.alarm.collect.squash
import com.homeshop.alarm.collect.str
import java.time.Instant
import java.time.LocalDate

/**
 * CJ온스타일 TV 편성표.
 * 출처: https://display.cjonstyle.com/c/rest/tv/tvSchedule?bdDt=YYYYMMDD&broadType=live (JSON, 2026-10 확인)
 * - result.programList[]: pgmNm, bdStrDtm/bdEndDtm(epoch ms), itemList[](itemCd, itemNm, salePrice, itemImgUrl, chnCd)
 * - broadType=plus 로 바꾸면 T커머스(CJ온스타일+).
 */
object CjOnstyle : ChannelSource {
    override val name = "CJ온스타일"
    private const val BASE = "https://display.cjonstyle.com"
    private const val IMG_BASE = "https://thumb.cjonstyle.net/unsafe/fit-in/300x300/itemimage.cjonstyle.net/"

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day))

    fun fetch(http: Http, day: LocalDate, broadType: String = "live"): String =
        http.get("$BASE/c/rest/tv/tvSchedule?bdDt=${day.format(YMD)}&isMobile=false&broadType=$broadType&isEmployee=false",
            mapOf("Referer" to "$BASE/p/homeTab/main?hmtabMenuId=002409&rPIC=schedulePC", "Accept" to "application/json"))

    fun parse(json: String, channel: String = name): List<Broadcast> =
        parseJson(json)["result"]["programList"].list.mapNotNull { p ->
            val start = p["bdStrDtm"].long ?: return@mapNotNull null
            val end = p["bdEndDtm"].long ?: return@mapNotNull null
            val products = p["itemList"].list.map { it ->
                val code = it["itemCd"].str.orEmpty()
                Product(
                    id = code,
                    name = it["itemNm"].str.orEmpty().squash().trimEnd('.'),
                    price = it["salePrice"].int,
                    image = it["itemImgUrl"].str?.let { img -> IMG_BASE + img },
                    url = "$BASE/p/item/$code?channelCode=${it["chnCd"].str.orEmpty()}",
                )
            }
            if (products.isEmpty()) return@mapNotNull null      // 상품 없는 배너 프로그램
            Broadcast(channel, epoch(start), epoch(end), p["pgmNm"].str ?: products.first().name, products.toMutableList())
        }.sortedBy { it.start }

    private fun epoch(ms: Long) = Instant.ofEpochMilli(ms).atOffset(KST)
}
