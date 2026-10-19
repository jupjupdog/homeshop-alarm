"""저장해 둔 실제 응답 샘플로 파서를 검증한다.  실행: python -m unittest discover -s collector/tests -t ."""
import json
import unittest
from datetime import date
from pathlib import Path

from collector.categorize import categorize
from collector.main import enrich
from collector.normalize import to_search_query
from collector.sources import cjonstyle, gsshop

FIX = Path(__file__).parent / "fixtures"


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


class CollectTest(unittest.TestCase):
    def test_zero_items_is_reported_as_error(self):
        from unittest import mock
        from collector import main
        with mock.patch.object(main.gsshop, "fetch", return_value="<html>차단</html>"), \
             mock.patch.object(main.cjonstyle, "fetch", return_value={}), \
             mock.patch.object(main.cjonstyle, "parse", return_value=[]):
            items, errors = main.collect(1)
        self.assertEqual(items, [])
        self.assertEqual(len(errors), 2)
        self.assertIn("0건", errors[0])


if __name__ == "__main__":
    unittest.main()
