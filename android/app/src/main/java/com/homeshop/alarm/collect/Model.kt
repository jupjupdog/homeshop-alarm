package com.homeshop.alarm.collect

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 홈쇼핑 편성표는 모두 한국 시간 기준. */
val KST: ZoneOffset = ZoneOffset.ofHours(9)

/** 모든 채널 파서의 공통 출력. */
data class Product(
    val id: String,
    val name: String,
    var price: Int?,
    val image: String?,
    val url: String?,
    var category: String = "기타",     // Categorizer 가 채움
    var searchQuery: String = "",      // SearchQuery 가 채움 (최저가 검색용)
)

data class Broadcast(
    val channel: String,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val title: String,                 // 프로그램명 (없으면 대표 상품명)
    val products: MutableList<Product> = mutableListOf(),
)

/** 채널 하나. collect() 는 네트워크를 쓰는 블로킹 함수라 IO 스레드에서 부른다. */
interface ChannelSource {
    val name: String
    fun collect(http: Http, day: LocalDate): List<Broadcast>
}

/** 상품명 공백 정리: 줄바꿈·연속 공백을 한 칸으로. */
internal fun String.squash(): String = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

/** "79,900원" → 79900. 숫자가 없으면 null. */
internal fun String?.digitsToInt(): Int? = this?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
