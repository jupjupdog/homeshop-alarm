"""NS홈쇼핑 TV 편성표 파서.

데이터 출처: https://mapi.nsmall.com/md/api/v1/display/media/schedule/tv/YYYYMMDD
 - 모바일 편성표(m.nsmall.com/schedule/tv)가 부르는 JSON (2026-10 확인).
 - tv 대신 shopplus 는 NS샵+(T커머스), all 은 둘 다.
 - data.resultData.totalOrgan[] 1건 = 방송 1개: startDtm/endDtm(YYYYMMDDHHMMSS), pgmNm,
   goods(대표 상품), relTotalOrgan[](함께 방송하는 상품)
 - 다음날 첫 방송이 마지막 항목으로 같이 온다(수집기에서 중복 제거).
"""
from __future__ import annotations

from datetime import date, datetime

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNEL = "NS홈쇼핑"
URL = "https://mapi.nsmall.com/md/api/v1/display/media/schedule/{kind}/{ymd}"
IMG_URL = "https://product-image.prod-nsmall.com/new/{goods}_F.jpg"
ITEM_URL = "https://m.nsmall.com/goods/{goods}"


def fetch(session, day: date, kind: str = "tv") -> dict:
    resp = session.get(URL.format(kind=kind, ymd=day.strftime("%Y%m%d")), timeout=20,
                       headers={"Origin": "https://m.nsmall.com", "Referer": "https://m.nsmall.com/",
                                "Accept": "application/json"})
    resp.raise_for_status()
    return resp.json()


def _dt(s: str) -> datetime:
    return datetime.strptime(s, "%Y%m%d%H%M%S").replace(tzinfo=KST)


def _product(goods_cd, goods: dict) -> Product:
    return Product(
        id=str(goods_cd),
        name=" ".join(str(goods.get("productNm") or "").split()),
        price=goods.get("applyPrice") or goods.get("salePrice"),
        image=IMG_URL.format(goods=goods_cd),
        url=ITEM_URL.format(goods=goods_cd),
    )


def parse(data: dict, channel: str = CHANNEL) -> list[Broadcast]:
    organ = (((data or {}).get("data") or {}).get("resultData") or {}).get("totalOrgan") or []
    result: list[Broadcast] = []
    for x in organ:
        if not x.get("startDtm") or not x.get("endDtm") or not x.get("goods"):
            continue
        products = [_product(x["goodsCd"], x["goods"])]
        products += [_product(r["goodsCd"], r.get("goods") or {}) for r in x.get("relTotalOrgan") or []
                     if r.get("goodsCd") and r["goodsCd"] != x["goodsCd"]]
        result.append(Broadcast(channel=channel, start=_dt(x["startDtm"]), end=_dt(x["endDtm"]),
                                title=x.get("pgmNm") or products[0].name, products=products))
    result.sort(key=lambda b: b.start)
    return result
