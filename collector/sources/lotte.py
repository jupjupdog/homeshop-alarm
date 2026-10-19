"""롯데홈쇼핑 TV 편성표 파서.

데이터 출처: https://www.lotteimall.com/main/scheduleLive.lotte?bdDate=YYYYMMDD
 - 편성표 '라이브TV' 탭이 부르는 JSON (2026-10 확인). 원TV(T커머스)는 scheduleOne.lotte.
 - body.prod[] 1건 = 방송 1개: sdate+stime ~ edate+etime, 대표 상품(goodsNo, name, price_disc)
 - related[] = 함께 방송하는 상품. relatedAdd(함께 사면 좋은 상품)는 방송 상품이 아니라 제외.
 - 전날 마지막 방송이 첫 항목으로 같이 온다(수집기에서 중복 제거).
"""
from __future__ import annotations

import re
from datetime import date, datetime

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNEL = "롯데홈쇼핑"
BASE = "https://www.lotteimall.com"
URL = BASE + "/main/scheduleLive.lotte?bdDate={ymd}"


def fetch(session, day: date) -> dict:
    resp = session.get(URL.format(ymd=day.strftime("%Y%m%d")), timeout=20,
                       headers={"Referer": BASE + "/main/tvschedule.lotte", "Accept": "application/json"})
    resp.raise_for_status()
    return resp.json()


def _price(text) -> int | None:
    digits = re.sub(r"[^\d]", "", str(text or ""))
    return int(digits) if digits and int(digits) > 0 else None   # 보험·상담 상품은 0원


def _product(p: dict, image_key: str) -> Product:
    return Product(
        id=str(p.get("goodsNo")),
        name=" ".join(str(p.get("name") or "").split()),
        price=_price(p.get("price_disc")),
        image=p.get(image_key) or p.get("img_url"),
        url=BASE + p["linkInfo"] if p.get("linkInfo") else None,
    )


def _dt(day: str, hm: str) -> datetime:
    return datetime.strptime(f"{day} {hm}", "%Y-%m-%d %H:%M").replace(tzinfo=KST)


def parse(data: dict, channel: str = CHANNEL) -> list[Broadcast]:
    result: list[Broadcast] = []
    for p in ((data or {}).get("body") or {}).get("prod") or []:
        if not (p.get("sdate") and p.get("stime") and p.get("edate") and p.get("etime")):
            continue
        products = [_product(p, "time_img_url")] + [_product(r, "img_url") for r in p.get("related") or []]
        result.append(Broadcast(channel=channel, start=_dt(p["sdate"], p["stime"]), end=_dt(p["edate"], p["etime"]),
                                title=products[0].name, products=products))
    result.sort(key=lambda b: b.start)
    return result
