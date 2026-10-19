"""편성표 수집 → 정제/분류 → schedule.json 생성.

사용법 (저장소 루트에서):
    python -m collector.main --out public/schedule.json --days 2

한 채널이 실패해도 다른 채널은 계속 수집하고, 실패 내용은 JSON의 errors 에 남긴다.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timedelta
from pathlib import Path

import requests

from .categorize import ALL_CATEGORY_NAMES, categorize
from .models import Broadcast
from .normalize import to_search_query
from .sources import cjonstyle, gongyoung, gsshop, hnsmall, hyundai, lotte, nsmall
from .timeutil import KST

USER_AGENT = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/128.0 Safari/537.36")

# (채널 이름, 날짜 하나를 수집하는 함수)
SOURCES = [
    ("GS샵", lambda s, d: gsshop.parse(gsshop.fetch(s, d), d)),
    ("CJ온스타일", lambda s, d: cjonstyle.parse(cjonstyle.fetch(s, d))),
    ("현대홈쇼핑", lambda s, d: hyundai.parse(hyundai.fetch(s, d))),
    ("롯데홈쇼핑", lambda s, d: lotte.parse(lotte.fetch(s, d))),
    ("NS홈쇼핑", lambda s, d: nsmall.parse(nsmall.fetch(s, d))),
    ("홈앤쇼핑", lambda s, d: hnsmall.parse(hnsmall.fetch(s, d), d)),
    ("공영쇼핑", lambda s, d: gongyoung.parse(gongyoung.fetch(s, d))),
]


def enrich(broadcasts: list[Broadcast]) -> None:
    for b in broadcasts:
        for p in b.products:
            if p.price is not None and p.price <= 0:   # 렌탈·상담 상품은 0원으로 온다 → 가격 없음
                p.price = None
            p.category = categorize(p.name)
            p.search_query = to_search_query(p.name)


def dedupe(broadcasts: list[Broadcast]) -> list[Broadcast]:
    """날짜 경계 방송(전날 마지막·다음날 첫 방송)이 두 날짜에서 같이 오므로 (채널, 시작) 기준 1개만 남긴다."""
    seen: dict[tuple[str, datetime], Broadcast] = {}
    for b in broadcasts:
        seen.setdefault((b.channel, b.start), b)
    return list(seen.values())


def collect(days: int) -> tuple[list[Broadcast], list[str]]:
    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    today = datetime.now(KST).date()
    out: list[Broadcast] = []
    errors: list[str] = []

    for i in range(days):
        d = today + timedelta(days=i)
        for name, job in SOURCES:
            label = f"{name} {d}"
            try:
                items = job(session, d)
                if not items:  # 차단 페이지·구조 변경은 200 응답에 0건으로 나타난다
                    raise ValueError("0건 (접속 차단 또는 페이지 구조 변경 의심)")
                print(f"[ok] {label}: {len(items)}건")
                out.extend(items)
            except Exception as e:  # noqa: BLE001 - 채널별 실패 격리
                msg = f"{label}: {type(e).__name__}: {e}"
                print(f"[fail] {msg}", file=sys.stderr)
                errors.append(msg)

    out = dedupe(out)
    enrich(out)
    out.sort(key=lambda b: (b.start, b.channel))
    return out, errors


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="public/schedule.json")
    ap.add_argument("--days", type=int, default=2, help="오늘부터 며칠치를 수집할지")
    args = ap.parse_args()

    broadcasts, errors = collect(args.days)
    payload = {
        "version": 1,
        "generatedAt": datetime.now(KST).isoformat(),
        "categories": ALL_CATEGORY_NAMES,
        "errors": errors,
        "broadcasts": [b.to_json() for b in broadcasts],
    }
    path = Path(args.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"saved {len(broadcasts)} broadcasts -> {path}")
    # 모든 채널이 실패했을 때만 실패 처리(기존 JSON 유지 목적)
    return 1 if not broadcasts else 0


if __name__ == "__main__":
    sys.exit(main())
