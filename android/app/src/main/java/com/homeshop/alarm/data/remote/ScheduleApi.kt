package com.homeshop.alarm.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** collector/main.py 가 만드는 schedule.json 형식 (version 1). */
@Serializable
data class ScheduleDto(
    val version: Int = 1,
    val generatedAt: String = "",
    val categories: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val broadcasts: List<BroadcastDto> = emptyList(),
)

@Serializable
data class BroadcastDto(
    val channel: String,
    val start: String,   // ISO-8601, +09:00
    val end: String,
    val title: String,
    val products: List<ProductDto> = emptyList(),
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val price: Int? = null,
    val image: String? = null,
    val url: String? = null,
    val category: String = "기타",
    val search_query: String = "",
)

class ScheduleApi(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun fetch(): ScheduleDto = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("편성표 요청 실패: HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("편성표 응답이 비어 있음")
            json.decodeFromString(ScheduleDto.serializer(), body)
        }
    }
}
