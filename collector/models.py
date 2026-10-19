"""수집기 공통 데이터 모델. 모든 채널 파서는 이 형식으로 출력한다."""
from __future__ import annotations

from dataclasses import dataclass, field, asdict
from datetime import datetime


@dataclass
class Product:
    id: str                 # 채널 내부 상품 ID
    name: str               # 원본 상품명
    price: int | None       # 판매가(원)
    image: str | None
    url: str | None
    category: str = "기타"   # categorize.py 가 채움
    search_query: str = ""  # normalize.py 가 채움 (최저가 검색용)


@dataclass
class Broadcast:
    channel: str            # 예: "GS샵", "CJ온스타일"
    start: datetime         # KST, tz-aware
    end: datetime
    title: str              # 프로그램명 (없으면 대표 상품명)
    products: list[Product] = field(default_factory=list)

    def to_json(self) -> dict:
        d = asdict(self)
        d["start"] = self.start.isoformat()
        d["end"] = self.end.isoformat()
        return d
