package com.homeshop.alarm.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(KST)
private val DAY_TIME = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN).withZone(KST)
private val WON = NumberFormat.getNumberInstance(Locale.KOREA)

fun formatTime(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis))
fun formatDayTime(millis: Long): String = DAY_TIME.format(Instant.ofEpochMilli(millis))
fun formatPrice(price: Int?): String = price?.let { WON.format(it) + "원" } ?: "가격 정보 없음"
