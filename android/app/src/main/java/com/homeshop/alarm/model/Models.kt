package com.homeshop.alarm.model

/** 방송 1건에 포함된 상품 1개 (화면·알림에서 쓰는 도메인 모델). */
data class ProductItem(
    val key: String,              // "채널|상품ID|시작시각" — 앱 전체에서 쓰는 고유 키
    val channel: String,
    val broadcastTitle: String,
    val startMillis: Long,
    val endMillis: Long,
    val productId: String,
    val name: String,
    val price: Int?,
    val imageUrl: String?,
    val productUrl: String?,
    val category: String,
    val searchQuery: String,
) {
    fun isOnAir(now: Long = System.currentTimeMillis()) = now in startMillis until endMillis
}

/** 수집기(collector/categorize.py)와 반드시 같은 이름을 써야 한다. */
object Categories {
    val ALL = listOf("패션", "뷰티", "식품", "건강", "가전·디지털", "리빙·주방", "여행·렌탈·보험", "기타")
}
