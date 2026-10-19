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
import com.homeshop.alarm.HomeshopApp
import java.util.concurrent.TimeUnit

/** 편성표를 받아 DB 를 갱신하고, 관심 방송 알람을 다시 예약한다. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as HomeshopApp).container
        return try {
            c.scheduleRepository.refresh()
            c.alarmScheduler.reschedule(c.scheduleRepository.upcoming())
            Result.success()
        } catch (e: Exception) {
            // 네트워크 실패여도 기존 캐시로 알람은 다시 맞춰 둔다
            runCatching { c.alarmScheduler.reschedule(c.scheduleRepository.upcoming()) }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "schedule-sync-periodic"
        private const val ONCE = "schedule-sync-once"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 1시간마다 동기화 (WorkManager 최소 주기는 15분). */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
