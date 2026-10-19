package com.homeshop.alarm.collect.sources

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ChannelSource
import com.homeshop.alarm.collect.Http
import com.homeshop.alarm.collect.KST
import com.homeshop.alarm.collect.Product
import com.homeshop.alarm.collect.get
import com.homeshop.alarm.collect.int
import com.homeshop.alarm.collect.list
import com.homeshop.alarm.collect.looseJson
import com.homeshop.alarm.collect.parseJson
import com.homeshop.alarm.collect.squash
import com.homeshop.alarm.collect.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * 현대홈쇼핑 TV 편성표.
 * 출처: https://www.hmall.com/api/hf/dp/v1/main-tv-new/tv-list?brodDt=YYYYMMDD&brodPrrgPage=N (JSON, 2026-10 확인)
 * - brodPrrgPage 1~6 = 하루를 4시간씩 나눈 페이지. 페이지를 빼면 0~4시만 온다.
 * - respData.broadItemList[] 1건 = 편성 상품 1개. 방송 = (brodChnlNm, brodStrtDtmParam) 로 묶는다.
 * - brodChnlNm: etv = 현대홈쇼핑(TV), dtv = 현대홈쇼핑+샵(T커머스), mtv = 쇼핑라이브(모바일)
 * - 종료 시각이 HHMM59 로 끝나는 경우가 있어 1초를 더해 정각으로 맞춘다.
 */
object Hyundai : ChannelSource {
    override val name = "현대홈쇼핑"
    val CHANNELS = mapOf("etv" to "현대홈쇼핑")
    private const val BASE = "https://www.hmall.com"

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day))

    /** 6페이지를 받아 {"broadItemList": [...]} 하나로 합친다 (테스트 샘플과 같은 모양). */
    fun fetch(http: Http, day: LocalDate): String {
        val items = (1..6).flatMap { page ->
            val text = http.get("$BASE/api/hf/dp/v1/main-tv-new/tv-list?brodDt=${day.format(YMD)}&brodPrrgPage=$page",
                mapOf("Referer" to "$BASE/md/dpl/tvScheduleSearch", "Accept" to "application/json"))
            parseJson(text)["respData"]["broadItemList"].list
        }
        return looseJson.encodeToString(JsonObject.serializer(), JsonObject(mapOf("broadItemList" to JsonArray(items))))
    }

    fun parse(json: String, channels: Map<String, String> = CHANNELS): List<Broadcast> {
        val broadcasts = linkedMapOf<Pair<String, OffsetDateTime>, Broadcast>()
        for (it in parseJson(json)["broadItemList"].list) {
            val channel = channels[it["brodChnlNm"].str] ?: continue
            val start = dt(it["brodStrtDtmParam"].str ?: continue)
            val end = dt(it["brodEndDtmParam"].str ?: continue)
            val code = it["slitmCd"].str.orEmpty()
            val product = Product(
                id = code,
                name = it["slitmNm"].str.orEmpty().squash(),
                price = it["bbprc"].int?.takeIf { p -> p > 0 } ?: it["sellPrc"].int,
                image = image(code, it["itemBaseImgNm"].str),
                url = "$BASE/md/pda/itemPtc?slitmCd=$code",
            )
            val b = broadcasts.getOrPut(channel to start) { Broadcast(channel, start, end, product.name) }
            if (b.products.none { p -> p.id == product.id }) b.products += product
        }
        return broadcasts.values.sortedBy { it.start }
    }

    private fun dt(s: String): OffsetDateTime {
        val t = LocalDateTime.parse(s, YMDHMS).atOffset(KST)
        return if (t.second == 59) t.plusSeconds(1) else t
    }

    /** Hmall 이미지 경로: static/{8번째}/{7번째}/{5~6번째}/{3~4번째}/파일명 (상품코드 기준). */
    private fun image(code: String, file: String?): String? {
        if (file == null || code.length < 8) return null
        return "https://image.hmall.com/static/${code[7]}/${code[6]}/${code.substring(4, 6)}/${code.substring(2, 4)}/$file"
    }
}
