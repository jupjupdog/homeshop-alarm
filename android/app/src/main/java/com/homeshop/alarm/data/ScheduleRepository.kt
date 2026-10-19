package com.homeshop.alarm.data

import com.homeshop.alarm.collect.Broadcast
import com.homeshop.alarm.collect.ScheduleCollector
import com.homeshop.alarm.data.local.ScheduleDao
import com.homeshop.alarm.data.local.ScheduleProductEntity
import com.homeshop.alarm.model.ProductItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/** 로컬 DB 가 화면의 기준(source of truth), 수집기는 DB 를 갱신하는 역할만 한다. */
class ScheduleRepository(
    private val collector: ScheduleCollector,
    private val dao: ScheduleDao,
) {
    fun observeUpcoming(now: Long = System.currentTimeMillis()): Flow<List<ProductItem>> =
        dao.observeUpcoming(now).map { rows -> rows.map { it.toModel() } }

    fun observe(key: String): Flow<ProductItem?> = dao.observe(key).map { it?.toModel() }

    suspend fun upcoming(now: Long = System.currentTimeMillis()): List<ProductItem> =
        dao.upcoming(now).map { it.toModel() }

    /**
     * 각 홈쇼핑 사이트에서 오늘·내일 편성표를 직접 받아 DB 에 반영한다.
     * 모든 채널이 실패하면(오프라인 등) 예외를 던지고 기존 캐시는 그대로 둔다.
     */
    suspend fun refresh(now: Long = System.currentTimeMillis()): ScheduleCollector.Result {
        val result = collector.collect()
        if (result.broadcasts.isEmpty()) {
            throw IOException("편성표를 받지 못했습니다. 인터넷 연결을 확인해 주세요.")
        }
        dao.merge(result.completeChannels.toList(), toEntities(result.broadcasts), endedBefore = now - DAY_MS)
        return result
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        fun toEntities(broadcasts: List<Broadcast>): List<ScheduleProductEntity> =
            broadcasts.flatMap { b ->
                val start = b.start.toInstant().toEpochMilli()
                val end = b.end.toInstant().toEpochMilli()
                b.products.map { p ->
                    ScheduleProductEntity(
                        key = "${b.channel}|${p.id}|$start",
                        channel = b.channel,
                        broadcastTitle = b.title,
                        startMillis = start,
                        endMillis = end,
                        productId = p.id,
                        name = p.name,
                        price = p.price,
                        imageUrl = p.image,
                        productUrl = p.url,
                        category = p.category,
                        searchQuery = p.searchQuery.ifBlank { p.name },
                    )
                }
            }.distinctBy { it.key }
    }
}
