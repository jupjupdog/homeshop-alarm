"""GS샵 TV 편성표 파서.

데이터 출처: https://www.gsshop.com/shop/tv/tvScheduleDetail.gs?today=YYYYMMDD
 - 편성표 페이지가 내부적으로 불러오는 HTML 조각(2026-10 확인).
 - 방송 1건 = <article class="items">, 시작 시각은 <span class="times">HH:MM</span>
 - 상품 1건 = <li class="prd-item">, 상품 링크의 brddate=YYYYMMDDHHMMSS 가 정확한 방송 시작 시각
 - 종료 시각은 제공되지 않으므로 다음 방송 시작 시각을 종료로 쓴다.
"""
from __future__ import annotations

import re
from datetime import date, datetime, timedelta
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNEL = "GS샵"
BASE = "https://www.gsshop.com"
URL = BASE + "/shop/tv/tvScheduleDetail.gs?today={ymd}"
DEFAULT_LAST_DURATION = timedelta(minutes=60)


def fetch(session, day: date) -> str:
    resp = session.get(URL.format(ymd=day.strftime("%Y%m%d")), timeout=20,
                       headers={"Referer": BASE + "/shop/tv/tvScheduleMain.gs"})
    resp.raise_for_status()
    return resp.text


def _int_price(text: str | None) -> int | None:
    if not text:
        return None
    digits = re.sub(r"[^\d]", "", text)
    return int(digits) if digits else None


def _start_from_link(href: str | None) -> datetime | None:
    if not href:
        return None
    brd = parse_qs(urlparse(href).query).get("brddate", [None])[0]
    if not brd or len(brd) < 12:
        return None
    return datetime.strptime(brd[:12], "%Y%m%d%H%M").replace(tzinfo=KST)


def parse(html: str, day: date) -> list[Broadcast]:
    soup = BeautifulSoup(html, "html.parser")
    raw: list[tuple[datetime, list[Product]]] = []

    for art in soup.select("article.items"):
        products: list[Product] = []
        start: datetime | None = None
        for li in art.select("li.prd-item"):
            name_a = li.select_one("dt.prd-name a")
            if not name_a:
                continue
            for tag in name_a.select("span"):          # "[TV상품]" 같은 라벨 제거
                tag.decompose()
            name = " ".join(name_a.get_text(" ", strip=True).split())
            href = name_a.get("href")
            start = start or _start_from_link(href)
            img = li.select_one("a.prd-img img")
            price = li.select_one("dd.prd-price .set-price strong")
            prd_id = (li.get("id") or "").removeprefix("li_")
            products.append(Product(
                id=prd_id,
                name=name,
                price=_int_price(price.get_text() if price else None),
                image=img.get("src") if img else None,
                url=urljoin(BASE, href) if href else None,
            ))

        if start is None:  # 링크가 없으면 화면의 HH:MM + 날짜로 계산
            t = art.select_one("aside .times")
            if not t:
                continue
            hh, mm = t.get_text(strip=True).split(":")
            start = datetime(day.year, day.month, day.day, int(hh), int(mm), tzinfo=KST)
        if products:
            raw.append((start, products))

    raw.sort(key=lambda x: x[0])
    result: list[Broadcast] = []
    for i, (start, products) in enumerate(raw):
        end = raw[i + 1][0] if i + 1 < len(raw) else start + DEFAULT_LAST_DURATION
        result.append(Broadcast(channel=CHANNEL, start=start, end=end,
                                title=products[0].name, products=products))
    return result
