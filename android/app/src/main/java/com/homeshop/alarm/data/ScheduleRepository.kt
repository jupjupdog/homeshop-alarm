package com.homeshop.alarm.data

import com.homeshop.alarm.data.local.ScheduleDao
import com.homeshop.alarm.data.local.ScheduleProductEntity
import com.homeshop.alarm.data.remote.ScheduleApi
import com.homeshop.alarm.data.remote.ScheduleDto
import com.homeshop.alarm.model.ProductItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.OffsetDateTime

/** 로컬 DB 가 화면의 기준(source of truth), 네트워크는 DB 를 갱신하는 역할만 한다. */
class ScheduleRepository(
    private val api: ScheduleApi,
    private val dao: ScheduleDao,
) {
    fun observeUpcoming(now: Long = System.currentTimeMillis()): Flow<List<ProductItem>> =
        dao.observeUpcoming(now).map { rows -> rows.map { it.toModel() } }

    fun observe(key: String): Flow<ProductItem?> = dao.observe(key).map { it?.toModel() }

    suspend fun upcoming(now: Long = System.currentTimeMillis()): List<ProductItem> =
        dao.upcoming(now).map { it.toModel() }

    /** 원격 편성표를 받아 DB 를 통째로 교체. 받은 상품 수를 돌려준다. */
    suspend fun refresh(): Int {
        val rows = toEntities(api.fetch())
        if (rows.isNotEmpty()) dao.replaceAll(rows)   // 빈 응답이면 기존 캐시 유지
        return rows.size
    }

    companion object {
        fun toEntities(dto: ScheduleDto): List<ScheduleProductEntity> =
            dto.broadcasts.flatMap { b ->
                val start = OffsetDateTime.parse(b.start).toInstant().toEpochMilli()
                val end = OffsetDateTime.parse(b.end).toInstant().toEpochMilli()
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
                        searchQuery = p.search_query.ifBlank { p.name },
                    )
                }
            }.distinctBy { it.key }
    }
}
