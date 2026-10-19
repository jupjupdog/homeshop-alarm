"""홈쇼핑 상품명을 최저가 검색용 검색어로 정제한다.

홈쇼핑 상품명에는 "[TV상품]", "[최초가 199,000원]", "3+1종 세트", "+사은품" 같은
판촉 문구가 많아서 그대로 검색하면 결과가 엉뚱해진다.
"""
from __future__ import annotations

import re

# 대괄호/소괄호 안의 판촉 문구
_BRACKETS = re.compile(r"\[[^\]]*\]|【[^】]*】|\((?:[^)]*(?:사은품|증정|특가|방송|한정|단독|최초가|무료)[^)]*)\)")
# 판촉/구성 단어
_PROMO_WORDS = re.compile(
    r"(방송에서만|방송\s*특가|단독\s*구성|단독|한정\s*수량|한정|특별\s*구성|최초가|역대\s*최저|"
    r"무료\s*배송|무이자|사은품|증정|TV\s*상품|론칭|런칭|앵콜|재방)"
)
# "3종", "3+1종", "10팩", "총 20개" 같은 수량 구성
_QUANTITY = re.compile(r"(총\s*)?\d+(\s*\+\s*\d+)?\s*(종|팩|개입|개|박스|병|봉|매|세트|SET|set|입)(\s*세트)?")
_PLUS_GIFT = re.compile(r"\+\s*[^\s]*(사은품|증정|선물)\S*")
_SEASON = re.compile(r"\b\d{2}(SS|FW|S/S|F/W)\b", re.IGNORECASE)
_MULTI_SPACE = re.compile(r"\s+")


def to_search_query(name: str, max_words: int = 6) -> str:
    q = name
    q = _BRACKETS.sub(" ", q)
    q = _PLUS_GIFT.sub(" ", q)
    q = _PROMO_WORDS.sub(" ", q)
    q = _QUANTITY.sub(" ", q)
    q = _SEASON.sub(" ", q)
    q = re.sub(r"[.,!~★☆♥/|]+", " ", q)
    q = _MULTI_SPACE.sub(" ", q).strip()
    words = q.split(" ")
    # 너무 긴 상품명은 앞쪽(브랜드+핵심 명사)만 사용
    return " ".join(words[:max_words]) if q else name.strip()
