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
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 공영쇼핑 TV 편성표.
 * 출처: POST https://www.gongyoungshop.kr/tvshopping/selectScheduleSub.do (brcStdDate=YYYYMMDD, 2026-10 확인)
 * - Accept: application/json 을 주지 않으면 XML 로 응답한다.
 * - prdList[] 1건 = 편성 상품 1개. 방송 = brcPgmSchdgId 로 묶고 prdBrcSeq 순서로 정렬.
 * - 이미지 fileName 의 _SIZE_ 를 지우면 원본(600KB 넘음), 뒤에 /dims/resize/Q_80,{폭} 을 붙이면 축소본.
 */
object Gongyoung : ChannelSource {
    override val name = "공영쇼핑"
    private const val BASE = "https://www.gongyoungshop.kr"
    private const val IMG_BASE = "https://img.publichs.com/ECMCFO/share/product"
    private const val THUMB = "/dims/resize/Q_80,384"

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day))

    fun fetch(http: Http, day: LocalDate): String =
        http.postForm("$BASE/tvshopping/selectScheduleSub.do", mapOf("brcStdDate" to day.format(YMD)),
            mapOf("Referer" to "$BASE/tvshopping/tvSchedule.do", "Accept" to "application/json",
                "X-Requested-With" to "XMLHttpRequest"))

    fun parse(json: String): List<Broadcast> {
        val rows = parseJson(json)["prdList"].list
            .sortedWith(compareBy({ it["brcBgnDtm"].str.orEmpty() }, { it["prdBrcSeq"].int ?: 0 }))
        val broadcasts = linkedMapOf<String, Broadcast>()
        for (x in rows) {
            val begin = x["brcBgnDtm"].str ?: continue
            val finish = x["brcEndDtm"].str ?: continue
            val prdId = x["prdId"].str ?: continue
            val product = Product(
                id = prdId,
                name = x["prdNm"].str.orEmpty().squash(),
                price = x["prdPrc"].int?.takeIf { it > 0 },
                image = x["fileName"].str?.let { IMG_BASE + it.replace("_SIZE_", "") + THUMB },
                url = "$BASE/goods/selectGoodsDetail.do?prdId=$prdId",
            )
            val b = broadcasts.getOrPut(x["brcPgmSchdgId"].str ?: begin) {
                Broadcast(name, dt(begin), dt(finish), x["brcPgmNm"].str ?: product.name)
            }
            if (b.products.none { it.id == product.id }) b.products += product
        }
        return broadcasts.values.sortedBy { it.start }
    }

    private fun dt(s: String) = LocalDateTime.parse(s, YMDHM).atOffset(KST)
}
