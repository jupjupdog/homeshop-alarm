"""현대홈쇼핑 TV 편성표 파서.

데이터 출처: https://www.hmall.com/api/hf/dp/v1/main-tv-new/tv-list?brodDt=YYYYMMDD&brodPrrgPage=N
 - Hmall 편성표 화면(/md/dpl/tvScheduleSearch)이 부르는 JSON (2026-10 확인).
 - brodPrrgPage 1~6 = 하루를 4시간씩 나눈 페이지. 페이지를 빼면 0~4시만 온다.
 - respData.broadItemList[] 1건 = 편성 상품 1개. 방송 = (brodChnlNm, brodStrtDtmParam) 로 묶는다.
 - brodChnlNm: etv = 현대홈쇼핑(TV), dtv = 현대홈쇼핑+샵(T커머스), mtv = 쇼핑라이브(모바일)
 - 종료 시각이 HHMM59 로 끝나는 경우가 있어 1초를 더해 정각으로 맞춘다.
"""
from __future__ import annotations

from datetime import date, datetime, timedelta

from ..models import Broadcast, Product
from ..timeutil import KST

CHANNELS = {"etv": "현대홈쇼핑"}
BASE = "https://www.hmall.com"
URL = BASE + "/api/hf/dp/v1/main-tv-new/tv-list"
PAGES = range(1, 7)
IMG_BASE = "https://image.hmall.com/static/"
ITEM_URL = BASE + "/md/pda/itemPtc?slitmCd={item}"


def fetch(session, day: date) -> dict:
    items: list[dict] = []
    for page in PAGES:
        resp = session.get(URL, params={"brodDt": day.strftime("%Y%m%d"), "brodPrrgPage": page}, timeout=20,
                           headers={"Referer": BASE + "/md/dpl/tvScheduleSearch", "Accept": "application/json"})
        resp.raise_for_status()
        items += ((resp.json() or {}).get("respData") or {}).get("broadItemList") or []
    return {"broadItemList": items}


def _dt(s: str) -> datetime:
    t = datetime.strptime(s, "%Y%m%d%H%M%S").replace(tzinfo=KST)
    return t + timedelta(seconds=1) if t.second == 59 else t


def _image(item_cd: str, file_name: str | None) -> str | None:
    """Hmall 이미지 경로: static/{8번째}/{7번째}/{5~6번째}/{3~4번째}/파일명 (상품코드 기준)."""
    if not file_name or len(item_cd) < 8:
        return None
    return IMG_BASE + "/".join([item_cd[7], item_cd[6], item_cd[4:6], item_cd[2:4], file_name])


def parse(data: dict, channels: dict[str, str] = CHANNELS) -> list[Broadcast]:
    broadcasts: dict[tuple[str, datetime], Broadcast] = {}
    for it in (data or {}).get("broadItemList") or []:
        channel = channels.get(it.get("brodChnlNm"))
        if not channel or not it.get("brodStrtDtmParam") or not it.get("brodEndDtmParam"):
            continue
        start = _dt(it["brodStrtDtmParam"])
        item_cd = str(it.get("slitmCd") or "")
        product = Product(
            id=item_cd,
            name=" ".join(str(it.get("slitmNm") or "").split()),
            price=it.get("bbprc") or it.get("sellPrc"),
            image=_image(item_cd, it.get("itemBaseImgNm")),
            url=ITEM_URL.format(item=item_cd),
        )
        b = broadcasts.get((channel, start))
        if b is None:
            b = broadcasts[(channel, start)] = Broadcast(
                channel=channel, start=start, end=_dt(it["brodEndDtmParam"]), title=product.name)
        if all(p.id != product.id for p in b.products):
            b.products.append(product)
    return sorted(broadcasts.values(), key=lambda b: b.start)
