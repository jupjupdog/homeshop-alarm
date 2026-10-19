package com.homeshop.alarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserSettings(
    val interestCategories: Set<String> = emptySet(),
    val leadMinutes: Int = 10,          // 방송 시작 몇 분 전에 알릴지
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val INTERESTS = stringSetPreferencesKey("interest_categories")
        val LEAD = intPreferencesKey("lead_minutes")
        val SCHEDULED_ALARMS = stringSetPreferencesKey("scheduled_alarm_keys")
        val LAST_SYNC = longPreferencesKey("last_sync_millis")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map {
        UserSettings(
            interestCategories = it[Keys.INTERESTS] ?: emptySet(),
            leadMinutes = it[Keys.LEAD] ?: 10,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun toggleInterest(category: String) = context.dataStore.edit {
        val now = it[Keys.INTERESTS] ?: emptySet()
        it[Keys.INTERESTS] = if (category in now) now - category else now + category
    }

    suspend fun setLeadMinutes(minutes: Int) = context.dataStore.edit { it[Keys.LEAD] = minutes }

    /** 이전에 예약한 알람 키 — 편성 변경 시 취소하기 위해 보관한다. */
    suspend fun scheduledAlarmKeys(): Set<String> =
        context.dataStore.data.first()[Keys.SCHEDULED_ALARMS] ?: emptySet()

    suspend fun setScheduledAlarmKeys(keys: Set<String>) =
        context.dataStore.edit { it[Keys.SCHEDULED_ALARMS] = keys }

    /** 마지막으로 편성표 수집에 성공한 시각 — 앱을 열 때마다 다시 받지 않도록. */
    suspend fun lastSyncMillis(): Long = context.dataStore.data.first()[Keys.LAST_SYNC] ?: 0L

    suspend fun setLastSyncMillis(millis: Long) = context.dataStore.edit { it[Keys.LAST_SYNC] = millis }
}
