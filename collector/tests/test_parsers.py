"""저장해 둔 실제 응답 샘플로 파서를 검증한다.  실행: python -m unittest discover -s collector/tests -t ."""
import json
import unittest
from datetime import date
from pathlib import Path

from collector.categorize import categorize
from collector.main import enrich
from collector.normalize import to_search_query
from collector.sources import cjonstyle, gongyoung, gsshop, hnsmall, hyundai, lotte, nsmall

FIX = Path(__file__).parent / "fixtures"


def load_json(name):
    return json.loads((FIX / name).read_text(encoding="utf-8"))


def hm(dt):
    return dt.strftime("%m-%d %H:%M")


class GsShopTest(unittest.TestCase):
    def setUp(self):
        html = (FIX / "gsshop_20261019.html").read_text(encoding="utf-8")
        self.items = gsshop.parse(html, date(2026, 10, 19))

    def test_broadcasts_and_times(self):
        self.assertEqual(len(self.items), 3)
        self.assertEqual(self.items[0].start.strftime("%m-%d %H:%M"), "10-19 01:00")
        self.assertEqual(self.items[0].end, self.items[1].start)        # 종료 = 다음 방송 시작
        self.assertEqual(self.items[2].start.strftime("%H:%M"), "03:10")
        self.assertEqual(self.items[2].start.utcoffset().total_seconds(), 9 * 3600)

    def test_products(self):
        p = self.items[0].products[0]
        self.assertEqual(len(self.items[0].products), 2)
        self.assertEqual(p.name, "LG 통돌이 세탁기 19KG T19MX7A 미드 블랙")   # [TV상품] 제거됨
        self.assertEqual(p.price, 790000)
        self.assertEqual(p.id, "1101536964")
        self.assertTrue(p.url.startswith("https://www.gsshop.com/prd/prd.gs?prdid=1101536964"))
        self.assertEqual(p.image, "https://asset.m-gs.kr/prod/1101536964/1/200")


class CjOnstyleTest(unittest.TestCase):
    def setUp(self):
        data = json.loads((FIX / "cjonstyle_20261019.json").read_text(encoding="utf-8"))
        self.items = cjonstyle.parse(data)

    def test_broadcasts(self):
        self.assertEqual(len(self.items), 3)
        b = self.items[1]
        self.assertEqual(b.title, "심야재방 베스트 1부")
        self.assertEqual(b.start.strftime("%Y-%m-%d %H:%M"), "2026-10-19 01:30")
        self.assertEqual(b.end.strftime("%H:%M"), "02:45")
        self.assertEqual(len(b.products), 2)

    def test_product_fields(self):
        p = self.items[0].products[0]
        self.assertEqual(p.name, "아빠랑 저칼로리 잼 3종 세트")
        self.assertEqual(p.price, 39900)
        self.assertIn("itemimage.cjonstyle.net/goods_images/20/614/2085428614L.jpg", p.image)
        self.assertEqual(p.url, "https://display.cjonstyle.com/p/item/2085428614?channelCode=30002002")


class NormalizeAndCategoryTest(unittest.TestCase):
    def test_search_query(self):
        self.assertEqual(to_search_query("[최초가 199,000원] 26SS 램스킨 레더 트리밍 자켓"), "램스킨 레더 트리밍 자켓")
        self.assertEqual(to_search_query("26FW 올데이 액티브 셋업 3+1종 세트 남성"), "올데이 액티브 셋업 남성")
        self.assertEqual(to_search_query("뷰 유자 멜라토닝 세럼 30ml 6개 방송특가"), "뷰 유자 멜라토닝 세럼 30ml")
        self.assertEqual(to_search_query("LG 통돌이 세탁기 19KG T19MX7A 미드 블랙"), "LG 통돌이 세탁기 19KG T19MX7A 미드")

    def test_categories(self):
        self.assertEqual(categorize("[TV상품] 쏘울 26FW 울 저지 더블 재킷"), "패션")
        self.assertEqual(categorize("LG 통돌이 세탁기 19KG"), "가전·디지털")
        self.assertEqual(categorize("뷰 유자 멜라토닝 세럼 30ml"), "뷰티")
        self.assertEqual(categorize("아빠랑 저칼로리 잼 3종 세트"), "식품")
        self.assertEqual(categorize("전통남원추어탕 300g 10팩"), "식품")

    def test_enrich_end_to_end(self):
        html = (FIX / "gsshop_20261019.html").read_text(encoding="utf-8")
        items = gsshop.parse(html, date(2026, 10, 19))
        enrich(items)
        self.assertEqual({p.category for b in items for p in b.products}, {"가전·디지털", "뷰티", "패션"})
        json.dumps([b.to_json() for b in items], ensure_ascii=False)  # 직렬화 가능


class HyundaiTest(unittest.TestCase):
    def setUp(self):
        self.data = load_json("hyundai_20261020.json")

    def test_groups_tv_channel_only(self):
        items = hyundai.parse(self.data)   # etv(TV)만. dtv(+샵)·mtv(모바일 쇼라)는 제외
        self.assertEqual([hm(b.start) for b in items], ["10-20 08:15", "10-20 10:25", "10-20 11:35"])
        self.assertEqual({b.channel for b in items}, {"현대홈쇼핑"})
        self.assertEqual(hm(items[0].end), "10-20 10:25")
        self.assertEqual(len(items[0].products), 3)            # 같은 방송 상품 3개가 묶임

    def test_product_fields(self):
        p = hyundai.parse(self.data)[0].products[0]
        self.assertEqual(p.id, "2253619262")
        self.assertEqual(p.price, 469000)
        self.assertEqual(p.image, "https://image.hmall.com/static/2/9/61/53/2253619262_0.jpg")
        self.assertEqual(p.url, "https://www.hmall.com/md/pda/itemPtc?slitmCd=2253619262")

    def test_end_59_seconds_rounded(self):
        items = hyundai.parse(self.data, {"dtv": "현대홈쇼핑+샵"})
        self.assertEqual((hm(items[0].start), hm(items[0].end)), ("10-20 08:40", "10-20 09:40"))


class LotteTest(unittest.TestCase):
    def setUp(self):
        self.items = lotte.parse(load_json("lotte_20261020.json"))

    def test_broadcasts(self):
        self.assertEqual([hm(b.start) for b in self.items], ["10-19 22:55", "10-19 23:55", "10-20 00:55"])
        self.assertEqual(hm(self.items[1].end), "10-20 00:55")   # 자정 넘김
        self.assertEqual(len(self.items[0].products), 4)       # 대표 + 함께 방송 3

    def test_product_fields(self):
        p = self.items[0].products[0]
        self.assertEqual(p.id, "12929554")
        self.assertEqual(p.price, 199000)
        self.assertEqual(p.url, "https://www.lotteimall.com/goods/viewGoodsDetail.lotte?goods_no=12929554")
        self.assertIsNone(self.items[1].products[0].price)     # 보험 상담 0원 → 가격 없음


class NsmallTest(unittest.TestCase):
    def setUp(self):
        self.items = nsmall.parse(load_json("nsmall_20261020.json"))

    def test_broadcasts(self):
        b = self.items[0]
        self.assertEqual(len(self.items), 3)
        self.assertEqual((hm(b.start), hm(b.end)), ("10-20 02:00", "10-20 02:40"))
        self.assertEqual(b.title, "N_히트상품앵콜방송 2부")
        self.assertEqual(len(b.products), 3)

    def test_product_fields(self):
        p = self.items[0].products[0]
        self.assertEqual(p.name, "안면도농협 태양초 햇 고춧가루 500gX4봉")
        self.assertEqual(p.price, 79900)
        self.assertEqual(p.image, "https://product-image.prod-nsmall.com/new/36224739_F.jpg")
        self.assertEqual(p.url, "https://m.nsmall.com/goods/36224739")


class HnsmallTest(unittest.TestCase):
    def setUp(self):
        html = (FIX / "hnsmall_20261020.html").read_text(encoding="utf-8")
        self.items = hnsmall.parse(html, date(2026, 10, 20))

    def test_broadcasts(self):
        self.assertEqual([hm(b.start) for b in self.items], ["10-20 01:00", "10-20 02:00", "10-20 23:55"])
        self.assertEqual(hm(self.items[-1].end), "10-21 01:00")  # 23:55 ~ 01:00
        self.assertEqual(len(self.items[0].products), 5)        # 대표 + 함께 방송 4

    def test_product_fields(self):
        p = self.items[0].products[0]
        self.assertEqual(p.id, "85754717")
        self.assertEqual(p.name, "[쿠쿠/10인용] 블랙스톤 전기압력밥솥 풀스텐 CRP-R1095FGW/FGB")
        self.assertEqual(p.price, 308000)
        self.assertEqual(p.image, "https://image.hnsmall.com/images/goods/717/85754717_wide.jpg")
        self.assertEqual(p.url, "https://www.hnsmall.com/display/goods.do?goods_code=85754717")


class GongyoungTest(unittest.TestCase):
    def setUp(self):
        self.items = gongyoung.parse(load_json("gongyoung_20261020.json"))

    def test_broadcasts(self):
        b = self.items[0]
        self.assertEqual([hm(x.start) for x in self.items], ["10-20 02:50", "10-20 03:20", "10-20 04:10"])
        self.assertEqual(hm(b.end), "10-20 03:20")
        self.assertEqual(b.title, "재방_건강식품")
        self.assertEqual([p.id for p in b.products], ["11955697", "11955718"])   # prdBrcSeq 순서

    def test_product_fields(self):
        p = self.items[0].products[0]
        self.assertEqual(p.price, 298000)
        self.assertEqual(p.image, "https://img.publichs.com/ECMCFO/share/product/97/56/95/18264526.jpg")
        self.assertEqual(p.url, "https://www.gongyoungshop.kr/goods/selectGoodsDetail.do?prdId=11955697")


class CollectTest(unittest.TestCase):
    def test_zero_items_is_reported_as_error(self):
        from unittest import mock
        from collector import main
        sources = [("가", lambda s, d: []), ("나", lambda s, d: [])]
        with mock.patch.object(main, "SOURCES", sources):
            items, errors = main.collect(1)
        self.assertEqual(items, [])
        self.assertEqual(len(errors), 2)
        self.assertIn("0건", errors[0])

    def test_day_boundary_duplicates_removed(self):
        from collector import main
        a = lotte.parse(load_json("lotte_20261020.json"))
        self.assertEqual(len(main.dedupe(a + a[:2])), 3)


if __name__ == "__main__":
    unittest.main()
