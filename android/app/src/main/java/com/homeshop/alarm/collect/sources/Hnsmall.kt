package com.homeshop.alarm.collect.sources

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ChannelSource
import com.homeshop.alarm.collect.Http
import com.homeshop.alarm.collect.KST
import com.homeshop.alarm.collect.Product
import com.homeshop.alarm.collect.digitsToInt
import com.homeshop.alarm.collect.squash
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate

/**
 * 홈앤쇼핑 TV 편성표.
 * 출처: https://www.hnsmall.com/display/tvschedule-list?broadDay=YYYYMMDD (날짜를 누를 때 불러오는 HTML 조각, 2026-10 확인)
 * - 방송 = ul.list-box > li.item, 시각은 p.time span "HH:MM ~ HH:MM"
 * - 대표 상품 = .prd-box > .goods-list, 함께 방송하는 상품 = .sub-prd li
 * - 상품 ID 는 onclick 의 goGoods('85754717', ...) 에서 꺼낸다.
 */
object Hnsmall : ChannelSource {
    override val name = "홈앤쇼핑"
    private const val BASE = "https://www.hnsmall.com"
    private val GOODS = Regex("goGoods\\('(\\d+)'")
    private val TIME = Regex("(\\d{1,2}):(\\d{2})")

    override fun collect(http: Http, day: LocalDate) = parse(fetch(http, day), day)

    fun fetch(http: Http, day: LocalDate): String =
        http.get("$BASE/display/tvschedule-list?broadDay=${day.format(YMD)}",
            mapOf("Referer" to "$BASE/display/tvschedule", "X-Requested-With" to "XMLHttpRequest"))

    fun parse(html: String, day: LocalDate): List<Broadcast> =
        Jsoup.parse(html).select("ul.list-box > li.item").mapNotNull { li ->
            val times = TIME.findAll(li.selectFirst("p.time span")?.text().orEmpty()).toList()
            if (times.size < 2) return@mapNotNull null
            val (sh, sm) = times[0].destructured
            val (eh, em) = times[1].destructured
            val start = day.atTime(sh.toInt(), sm.toInt()).atOffset(KST)
            var end = day.atTime(eh.toInt(), em.toInt()).atOffset(KST)
            if (!end.isAfter(start)) end = end.plusDays(1)        // 23:55 ~ 01:00
            val products = (li.select(".prd-box > .goods-list") + li.select(".sub-prd li")).mapNotNull(::product)
            if (products.isEmpty()) null else Broadcast(name, start, end, products.first().name, products.toMutableList())
        }.sortedBy { it.start }

    private fun product(node: Element): Product? {
        val onclicks = listOf(node.attr("onclick")) + node.select("[onclick]").map { it.attr("onclick") }
        val goods = onclicks.firstNotNullOfOrNull { GOODS.find(it)?.groupValues?.get(1) } ?: return null
        val title = node.selectFirst(".tit") ?: return null
        val src = node.selectFirst("img")?.attr("src")?.ifEmpty { null }
        return Product(
            id = goods,
            name = title.text().squash(),
            price = node.selectFirst(".price strong")?.text().digitsToInt(),
            image = if (src != null && src.startsWith("//")) "https:$src" else src,
            url = "$BASE/display/goods.do?goods_code=$goods",
        )
    }
}
