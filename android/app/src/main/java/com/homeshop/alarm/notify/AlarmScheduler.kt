package com.homeshop.alarm.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.homeshop.alarm.data.SettingsRepository
import com.homeshop.alarm.model.ProductItem

/**
 * 관심 카테고리 방송의 시작 N분 전에 알람을 건다.
 *
 * - 같은 방송(채널+시작시각)에 관심 상품이 여러 개여도 알람은 1개만 건다.
 * - 정확한 알람 권한이 없으면(Android 14+ 기본값) 몇 분 오차가 있는 알람으로 대체한다.
 * - 이전 예약 중 더 이상 필요 없는 것은 취소한다(편성 변경·관심 해제 대응).
 */
class AlarmScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun reschedule(upcoming: List<ProductItem>, now: Long = System.currentTimeMillis()) {
        val s = settings.current()
        val leadMs = s.leadMinutes * 60_000L

        val targets: Map<String, List<ProductItem>> = upcoming
            .filter { it.category in s.interestCategories && it.startMillis - leadMs > now }
            .groupBy { broadcastKey(it) }

        val previous = settings.scheduledAlarmKeys()
        (previous - targets.keys).forEach { cancel(it) }

        targets.forEach { (key, items) ->
            val first = items.first()
            val triggerAt = first.startMillis - leadMs
            val intent = AlarmReceiver.intent(
                context,
                broadcastKey = key,
                productKey = first.key,
                channel = first.channel,
                title = items.joinToString(", ") { it.name }.take(80),
                category = first.category,
                startMillis = first.startMillis,
                extraCount = items.size - 1,
            )
            val pi = PendingIntent.getBroadcast(
                context, requestCode(key), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
        settings.setScheduledAlarmKeys(targets.keys)
    }

    private fun cancel(key: String) {
        val pi = PendingIntent.getBroadcast(
            context, requestCode(key), Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    companion object {
        fun broadcastKey(item: ProductItem) = "${item.channel}|${item.startMillis}"
        fun requestCode(key: String) = key.hashCode()
    }
}
