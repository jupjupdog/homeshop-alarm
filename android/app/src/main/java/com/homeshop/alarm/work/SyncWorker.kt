package com.homeshop.alarm.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.homeshop.alarm.HomeshopApp
import java.util.concurrent.TimeUnit

/**
 * 홈쇼핑 사이트에서 편성표를 직접 받아 DB 를 갱신하고, 관심 방송 알람을 다시 예약한다.
 * 한 번 수집에 약 5MB(7개 채널 × 오늘·내일)라서 자주 돌리지 않는다:
 * 6시간마다 + 앱 실행·재부팅 시(마지막 수집 후 2시간이 지났을 때만) + 새로고침 버튼.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as HomeshopApp).container
        val now = System.currentTimeMillis()
        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            if (force || now - c.settingsRepository.lastSyncMillis() > STALE_MS) {
                c.scheduleRepository.refresh(now)
                c.settingsRepository.setLastSyncMillis(now)
            }
            c.alarmScheduler.reschedule(c.scheduleRepository.upcoming())
            Result.success()
        } catch (e: Exception) {
            // 수집 실패여도 기존 캐시로 알람은 다시 맞춰 둔다
            runCatching { c.alarmScheduler.reschedule(c.scheduleRepository.upcoming()) }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "schedule-sync-periodic"
        private const val ONCE = "schedule-sync-once"
        private const val KEY_FORCE = "force"
        private val STALE_MS = TimeUnit.HOURS.toMillis(2)

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 6시간마다 수집. 예전(1시간 주기) 예약이 남아 있으면 새 주기로 바꾼다. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(network)
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** 앱 실행·재부팅 시. 최근에 받았으면 수집은 건너뛰고 알람만 다시 맞춘다. */
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
