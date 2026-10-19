"""CJ온스타일 TV 편성표 파서.

데이터 출처: https://display.cjonstyle.com/c/rest/tv/tvSchedule?bdDt=YYYYMMDD&isMobile=false&broadType=live&isEmployee=false
 - 편성표 페이지가 내부적으로 부르는 JSON (2026-10 확인).
 - result.programList[] : pgmNm, bdStrDtm/bdEndDtm (epoch ms), itemList[]
 - itemList[] : itemCd, itemNm, salePrice, itemImgUrl(상대 경로), chnCd
 - broadType=plus 로 바꾸면 T커머스 채널(CJ온스타일+)이 나온다.
"""
from __future__ import annotations

from datetime import date, datetime

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNEL = "CJ온스타일"
BASE = "https://display.cjonstyle.com"
URL = BASE + "/c/rest/tv/tvSchedule?bdDt={ymd}&isMobile=false&broadType={broad}&isEmployee=false"
IMG_BASE = "https://thumb.cjonstyle.net/unsafe/fit-in/300x300/itemimage.cjonstyle.net/"
ITEM_URL = BASE + "/p/item/{item}?channelCode={chn}"


def fetch(session, day: date, broad_type: str = "live") -> dict:
    resp = session.get(URL.format(ymd=day.strftime("%Y%m%d"), broad=broad_type), timeout=20,
                       headers={"Referer": BASE + "/p/homeTab/main?hmtabMenuId=002409&rPIC=schedulePC",
                                "Accept": "application/json"})
    resp.raise_for_status()
    return resp.json()


def _dt(ms) -> datetime:
    return datetime.fromtimestamp(int(ms) / 1000, tz=KST)


def parse(data: dict, channel: str = CHANNEL) -> list[Broadcast]:
    programs = ((data or {}).get("result") or {}).get("programList") or []
    result: list[Broadcast] = []
    for p in programs:
        if not p.get("bdStrDtm") or not p.get("bdEndDtm"):
            continue
        products = []
        for it in p.get("itemList") or []:
            img = it.get("itemImgUrl")
            products.append(Product(
                id=str(it.get("itemCd")),
                name=" ".join(str(it.get("itemNm") or "").split()).rstrip("."),
                price=it.get("salePrice"),
                image=(IMG_BASE + img) if img else None,
                url=ITEM_URL.format(item=it.get("itemCd"), chn=it.get("chnCd") or ""),
            ))
        if not products:  # 상품 없는 프로그램(배너 등)은 건너뜀
            continue
        result.append(Broadcast(channel=channel, start=_dt(p["bdStrDtm"]), end=_dt(p["bdEndDtm"]),
                                title=p.get("pgmNm") or products[0].name, products=products))
    result.sort(key=lambda b: b.start)
    return result
