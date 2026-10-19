package com.homeshop.alarm.collect

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 쇼핑몰 편성표 요청용 HTTP. 사이트들이 PC 브라우저 요청을 기준으로 응답하므로 PC 크롬 UA 를 쓴다. */
class Http(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(Request.Builder().url(url).headers(headers).build())

    fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(body).headers(headers).build())
    }

    private fun Request.Builder.headers(headers: Map<String, String>) = apply {
        header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> header(k, v) }
    }

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("빈 응답")
        }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0 Safari/537.36"
    }
}
