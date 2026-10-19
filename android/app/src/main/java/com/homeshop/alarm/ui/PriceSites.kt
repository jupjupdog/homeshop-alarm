package com.homeshop.alarm.ui

import java.net.URLEncoder

/**
 * 최저가 비교에 쓰는 쇼핑몰 검색 페이지.
 * 네이버 쇼핑 검색 API 가 2026-07-31 종료되어, 1차 버전은 각 사이트 검색 결과 페이지를 그대로 보여 준다.
 * 제휴 API(쿠팡 파트너스 등)를 붙이면 여기서 가격 목록을 직접 받아오는 구현으로 바꿀 수 있다.
 */
enum class PriceSite(val label: String, private val template: String) {
    NAVER("네이버쇼핑", "https://search.shopping.naver.com/search/all?query=%s&sort=price_asc"),
    DANAWA("다나와", "https://search.danawa.com/dsearch.php?query=%s&sort=priceASC"),
    COUPANG("쿠팡", "https://www.coupang.com/np/search?q=%s&sorter=salePriceAsc"),
    ELEVENST("11번가", "https://search.11st.co.kr/Search.tmall?kwd=%s&sortCd=L");

    fun url(query: String): String = template.format(URLEncoder.encode(query.trim(), "UTF-8"))
}
