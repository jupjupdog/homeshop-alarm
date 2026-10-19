"""공영쇼핑 TV 편성표 파서.

데이터 출처: POST https://www.gongyoungshop.kr/tvshopping/selectScheduleSub.do (brcStdDate=YYYYMMDD)
 - 편성표 화면(/tvshopping/tvSchedule.do)이 부르는 API (2026-10 확인).
 - Accept: application/json 을 주지 않으면 XML 로 응답한다.
 - prdList[] 1건 = 편성 상품 1개. 방송 = brcPgmSchdgId 로 묶고 prdBrcSeq 순서로 정렬.
 - brcBgnDtm/brcEndDtm = YYYYMMDDHHMM, brcPgmNm = 프로그램명(예: "재방_건강식품")
 - 이미지 fileName 의 _SIZE_ 를 지우면 원본 경로가 된다.
"""
from __future__ import annotations

from datetime import date, datetime

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNEL = "공영쇼핑"
BASE = "https://www.gongyoungshop.kr"
URL = BASE + "/tvshopping/selectScheduleSub.do"
IMG_BASE = "https://img.publichs.com/ECMCFO/share/product"
ITEM_URL = BASE + "/goods/selectGoodsDetail.do?prdId={prd}"


def fetch(session, day: date) -> dict:
    resp = session.post(URL, data={"brcStdDate": day.strftime("%Y%m%d")}, timeout=20,
                        headers={"Referer": BASE + "/tvshopping/tvSchedule.do", "Accept": "application/json",
                                 "X-Requested-With": "XMLHttpRequest"})
    resp.raise_for_status()
    return resp.json()


def _dt(s: str) -> datetime:
    return datetime.strptime(s, "%Y%m%d%H%M").replace(tzinfo=KST)


def parse(data: dict) -> list[Broadcast]:
    rows = sorted((data or {}).get("prdList") or [],
                  key=lambda x: (x.get("brcBgnDtm") or "", int(x.get("prdBrcSeq") or 0)))
    broadcasts: dict[str, Broadcast] = {}
    for x in rows:
        if not x.get("brcBgnDtm") or not x.get("brcEndDtm") or not x.get("prdId"):
            continue
        file_name = x.get("fileName")
        product = Product(
            id=str(x["prdId"]),
            name=" ".join(str(x.get("prdNm") or "").split()),
            price=x.get("prdPrc") or None,
            image=IMG_BASE + file_name.replace("_SIZE_", "") if file_name else None,
            url=ITEM_URL.format(prd=x["prdId"]),
        )
        key = x.get("brcPgmSchdgId") or x["brcBgnDtm"]
        b = broadcasts.get(key)
        if b is None:
            b = broadcasts[key] = Broadcast(channel=CHANNEL, start=_dt(x["brcBgnDtm"]), end=_dt(x["brcEndDtm"]),
                                            title=x.get("brcPgmNm") or product.name)
        if all(p.id != product.id for p in b.products):
            b.products.append(product)
    return sorted(broadcasts.values(), key=lambda b: b.start)
