package com.homeshop.alarm.collect

import com.homeshop.alarm.collect.sources.CjOnstyle
import com.homeshop.alarm.collect.sources.Gongyoung
import com.homeshop.alarm.collect.sources.GsShop
import com.homeshop.alarm.collect.sources.Hnsmall
import com.homeshop.alarm.collect.sources.Hyundai
import com.homeshop.alarm.collect.sources.Lotte
import com.homeshop.alarm.collect.sources.Nsmall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 저장해 둔 실제 응답(collector/tests/fixtures)으로 파서를 검증한다.
 * Python 수집기 테스트(collector/tests/test_parsers.py)와 같은 기대값.
 */
class ParsersTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun hm(t: OffsetDateTime) = t.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    private val oct19 = LocalDate.of(2026, 10, 19)
    private val oct20 = LocalDate.of(2026, 10, 20)

    @Test fun gsshop() {
        val items = GsShop.parse(fixture("gsshop_20261019.html"), oct19)
        assertEquals(3, items.size)
        assertEquals("10-19 01:00", hm(items[0].start))
        assertEquals(items[1].start, items[0].end)                 // 종료 = 다음 방송 시작
        assertEquals("10-19 03:10", hm(items[2].start))
        assertEquals(KST, items[2].start.offset)
        val p = items[0].products[0]
        assertEquals(2, items[0].products.size)
        assertEquals("LG 통돌이 세탁기 19KG T19MX7A 미드 블랙", p.name)  // [TV상품] 제거
        assertEquals(790_000, p.price)
        assertEquals("1101536964", p.id)
        assertTrue(p.url!!, p.url!!.startsWith("https://www.gsshop.com/prd/prd.gs?prdid=1101536964"))
        assertEquals("https://asset.m-gs.kr/prod/1101536964/1/200", p.image)
    }

    @Test fun cjonstyle() {
        val items = CjOnstyle.parse(fixture("cjonstyle_20261019.json"))
        assertEquals(3, items.size)
        val b = items[1]
        assertEquals("심야재방 베스트 1부", b.title)
        assertEquals("10-19 01:30", hm(b.start))
        assertEquals("10-19 02:45", hm(b.end))
        assertEquals(2, b.products.size)
        val p = items[0].products[0]
        assertEquals("아빠랑 저칼로리 잼 3종 세트", p.name)
        assertEquals(39_900, p.price)
        assertTrue(p.image!!.contains("itemimage.cjonstyle.net/goods_images/20/614/2085428614L.jpg"))
        assertEquals("https://display.cjonstyle.com/p/item/2085428614?channelCode=30002002", p.url)
    }

    @Test fun hyundai() {
        val json = fixture("hyundai_20261020.json")
        val items = Hyundai.parse(json)                            // etv(TV)만
        assertEquals(listOf("10-20 08:15", "10-20 10:25", "10-20 11:35"), items.map { hm(it.start) })
        assertEquals(setOf("현대홈쇼핑"), items.map { it.channel }.toSet())
        assertEquals("10-20 10:25", hm(items[0].end))
        assertEquals(3, items[0].products.size)
        val p = items[0].products[0]
        assertEquals("2253619262", p.id)
        assertEquals(469_000, p.price)
        assertEquals("https://image.hmall.com/static/2/9/61/53/2253619262_0.jpg", p.image)
        assertEquals("https://www.hmall.com/md/pda/itemPtc?slitmCd=2253619262", p.url)
        val plus = Hyundai.parse(json, mapOf("dtv" to "현대홈쇼핑+샵"))  // 종료 HHMM59 → 정각
        assertEquals("10-20 08:40" to "10-20 09:40", hm(plus[0].start) to hm(plus[0].end))
    }

    @Test fun lotte() {
        val items = Lotte.parse(fixture("lotte_20261020.json"))
        assertEquals(listOf("10-19 22:55", "10-19 23:55", "10-20 00:55"), items.map { hm(it.start) })
        assertEquals("10-20 00:55", hm(items[1].end))              // 자정 넘김
        assertEquals(4, items[0].products.size)                    // 대표 + 함께 방송 3
        val p = items[0].products[0]
        assertEquals("12929554", p.id)
        assertEquals(199_000, p.price)
        assertEquals("https://www.lotteimall.com/goods/viewGoodsDetail.lotte?goods_no=12929554", p.url)
        assertNull(items[1].products[0].price)                     // 보험 상담 0원
    }

    @Test fun nsmall() {
        val items = Nsmall.parse(fixture("nsmall_20261020.json"))
        val b = items[0]
        assertEquals(3, items.size)
        assertEquals("10-20 02:00" to "10-20 02:40", hm(b.start) to hm(b.end))
        assertEquals("N_히트상품앵콜방송 2부", b.title)
        assertEquals(3, b.products.size)
        val p = b.products[0]
        assertEquals("안면도농협 태양초 햇 고춧가루 500gX4봉", p.name)
        assertEquals(79_900, p.price)
        assertEquals("https://product-image.prod-nsmall.com/new/36224739_F.jpg", p.image)
        assertEquals("https://m.nsmall.com/goods/36224739", p.url)
    }

    @Test fun hnsmall() {
        val items = Hnsmall.parse(fixture("hnsmall_20261020.html"), oct20)
        assertEquals(listOf("10-20 01:00", "10-20 02:00", "10-20 23:55"), items.map { hm(it.start) })
        assertEquals("10-21 01:00", hm(items.last().end))           // 23:55 ~ 01:00
        assertEquals(5, items[0].products.size)
        val p = items[0].products[0]
        assertEquals("85754717", p.id)
        assertEquals("[쿠쿠/10인용] 블랙스톤 전기압력밥솥 풀스텐 CRP-R1095FGW/FGB", p.name)
        assertEquals(308_000, p.price)
        assertEquals("https://image.hnsmall.com/images/goods/717/85754717_wide.jpg", p.image)
        assertEquals("https://www.hnsmall.com/display/goods.do?goods_code=85754717", p.url)
    }

    @Test fun gongyoung() {
        val items = Gongyoung.parse(fixture("gongyoung_20261020.json"))
        val b = items[0]
        assertEquals(listOf("10-20 02:50", "10-20 03:20", "10-20 04:10"), items.map { hm(it.start) })
        assertEquals("10-20 03:20", hm(b.end))
        assertEquals("재방_건강식품", b.title)
        assertEquals(listOf("11955697", "11955718"), b.products.map { it.id })   // prdBrcSeq 순서
        val p = b.products[0]
        assertEquals(298_000, p.price)
        assertEquals("https://img.publichs.com/ECMCFO/share/product/97/56/95/18264526.jpg/dims/resize/Q_80,384", p.image)
        assertEquals("https://www.gongyoungshop.kr/goods/selectGoodsDetail.do?prdId=11955697", p.url)
    }

    @Test fun dayBoundaryDuplicatesRemoved() {
        val a = Lotte.parse(fixture("lotte_20261020.json"))
        assertEquals(3, ScheduleCollector.dedupe(a + a.take(2)).size)
    }
}
