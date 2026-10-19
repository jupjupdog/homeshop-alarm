package com.homeshop.alarm.collect

import com.homeshop.alarm.collect.sources.CjOnstyle
import com.homeshop.alarm.collect.sources.Gongyoung
import com.homeshop.alarm.collect.sources.GsShop
import com.homeshop.alarm.collect.sources.Hnsmall
import com.homeshop.alarm.collect.sources.Hyundai
import com.homeshop.alarm.collect.sources.Lotte
import com.homeshop.alarm.collect.sources.Nsmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 폰에서 각 홈쇼핑 사이트 편성표를 직접 받아 정리한다 (collector/main.py 와 같은 순서).
 * 채널·날짜별로 따로 받아서 한 곳이 실패해도 나머지는 살린다.
 */
class ScheduleCollector(
    private val http: Http = Http(),
    private val sources: List<ChannelSource> = ALL_SOURCES,
    private val today: () -> LocalDate = { LocalDate.now(KST) },
) {
    data class Result(
        val broadcasts: List<Broadcast>,
        val errors: List<String>,
        /** 모든 날짜를 받아 온 채널. 이 채널들만 DB 에서 통째로 교체한다. */
        val completeChannels: Set<String>,
    )

    private data class Job(val source: ChannelSource, val day: LocalDate, val items: List<Broadcast>?, val error: String?)

    suspend fun collect(days: Int = 2): Result = coroutineScope {
        val start = today()
        val jobs = sources.flatMap { source ->
            (0 until days).map { i ->
                val day = start.plusDays(i.toLong())
                async(Dispatchers.IO) {
                    try {
                        val items = source.collect(http, day)
                        // 차단 페이지·구조 변경은 정상 응답에 0건으로 나타난다
                        if (items.isEmpty()) Job(source, day, null, "0건 (접속 차단 또는 페이지 구조 변경 의심)")
                        else Job(source, day, items, null)
                    } catch (e: Exception) {
                        Job(source, day, null, "${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }
        }.awaitAll()

        val broadcasts = dedupe(jobs.flatMap { it.items.orEmpty() })
        enrich(broadcasts)
        Result(
            broadcasts = broadcasts.sortedWith(compareBy({ it.start }, { it.channel })),
            errors = jobs.filter { it.error != null }.map { "${it.source.name} ${it.day}: ${it.error}" },
            completeChannels = jobs.groupBy { it.source.name }.filterValues { js -> js.all { it.error == null } }.keys,
        )
    }

    companion object {
        val ALL_SOURCES: List<ChannelSource> = listOf(GsShop, CjOnstyle, Hyundai, Lotte, Nsmall, Hnsmall, Gongyoung)

        /** 날짜 경계 방송(전날 마지막·다음날 첫 방송)이 두 날짜에서 같이 오므로 (채널, 시작) 기준 1개만 남긴다. */
        fun dedupe(broadcasts: List<Broadcast>): List<Broadcast> {
            val seen = linkedMapOf<Pair<String, OffsetDateTime>, Broadcast>()
            for (b in broadcasts) seen.putIfAbsent(b.channel to b.start.toInstant().atOffset(KST), b)
            return seen.values.toList()
        }

        fun enrich(broadcasts: List<Broadcast>) {
            for (p in broadcasts.flatMap { it.products }) {
                if ((p.price ?: 1) <= 0) p.price = null       // 렌탈·상담 상품은 0원으로 온다
                p.category = Categorizer.categorize(p.name)
                p.searchQuery = SearchQuery.from(p.name)
            }
        }
    }
}
