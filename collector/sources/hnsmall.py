"""홈앤쇼핑 TV 편성표 파서.

데이터 출처: https://www.hnsmall.com/display/tvschedule-list?broadDay=YYYYMMDD
 - 편성표 화면(/display/tvschedule)이 날짜를 누를 때 불러오는 HTML 조각 (2026-10 확인).
 - 방송 1건 = <ul class="list-box"> 아래 <li class="item">, 시각은 p.time span "HH:MM ~ HH:MM"
 - 대표 상품 = .prd-box > .goods-list, 함께 방송하는 상품 = .sub-prd li
 - 상품 ID 는 onclick 의 goGoods('85754717', ...) 에서 꺼낸다.
"""
from __future__ import annotations

import re
from datetime import date, datetime, timedelta

from bs4 import BeautifulSoup

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNEL = "홈앤쇼핑"
BASE = "https://www.hnsmall.com"
URL = BASE + "/display/tvschedule-list?broadDay={ymd}"
ITEM_URL = BASE + "/display/goods.do?goods_code={goods}"
GOODS_RE = re.compile(r"goGoods\('(\d+)'")


def fetch(session, day: date) -> str:
    resp = session.get(URL.format(ymd=day.strftime("%Y%m%d")), timeout=20,
                       headers={"Referer": BASE + "/display/tvschedule", "X-Requested-With": "XMLHttpRequest"})
    resp.raise_for_status()
    return resp.text


def _product(node) -> Product | None:
    onclicks = [node.get("onclick") or ""] + [a.get("onclick") or "" for a in node.select("[onclick]")]
    goods = next((m.group(1) for m in map(GOODS_RE.search, onclicks) if m), None)
    name = node.select_one(".tit")
    if not goods or not name:
        return None
    price = node.select_one(".price strong")
    digits = re.sub(r"[^\d]", "", price.get_text()) if price else ""
    img = node.select_one("img")
    src = img.get("src") if img else None
    return Product(
        id=goods,
        name=" ".join(name.get_text(" ", strip=True).split()),
        price=int(digits) if digits else None,
        image="https:" + src if src and src.startswith("//") else src,
        url=ITEM_URL.format(goods=goods),
    )


def parse(html: str, day: date) -> list[Broadcast]:
    soup = BeautifulSoup(html, "html.parser")
    result: list[Broadcast] = []
    for li in soup.select("ul.list-box > li.item"):
        span = li.select_one("p.time span")
        times = re.findall(r"(\d{1,2}):(\d{2})", span.get_text(" ", strip=True)) if span else []
        if len(times) < 2:
            continue
        (sh, sm), (eh, em) = times[0], times[1]
        start = datetime(day.year, day.month, day.day, int(sh), int(sm), tzinfo=KST)
        end = datetime(day.year, day.month, day.day, int(eh), int(em), tzinfo=KST)
        if end <= start:   # 23:55 ~ 01:00 처럼 자정을 넘김
            end += timedelta(days=1)
        nodes = li.select(".prd-box > .goods-list") + li.select(".sub-prd li")
        products = [p for p in map(_product, nodes) if p]
        if products:
            result.append(Broadcast(channel=CHANNEL, start=start, end=end, title=products[0].name, products=products))
    result.sort(key=lambda b: b.start)
    return result
