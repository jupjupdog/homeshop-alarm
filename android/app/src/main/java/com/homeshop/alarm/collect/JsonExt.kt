package com.homeshop.alarm.collect

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 쇼핑몰 JSON 은 스키마가 제각각이라 DTO 대신 느슨하게 꺼내 쓴다. */
internal val looseJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement = looseJson.parseToJsonElement(text)

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal val JsonElement?.list: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()

/** 문자열이든 숫자든 문자열로. null·빈 값은 null. */
internal val JsonElement?.str: String?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotEmpty() }

internal val JsonElement?.int: Int? get() = str?.toDoubleOrNull()?.toInt()

internal val JsonElement?.long: Long? get() = str?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

internal val JsonElement?.bool: Boolean get() = str == "true"
