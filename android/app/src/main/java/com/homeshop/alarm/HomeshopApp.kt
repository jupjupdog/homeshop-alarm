package com.homeshop.alarm

import android.app.Application
import android.content.Context
import com.homeshop.alarm.data.ScheduleRepository
import com.homeshop.alarm.data.SettingsRepository
import com.homeshop.alarm.data.local.AppDatabase
import com.homeshop.alarm.data.remote.ScheduleApi
import com.homeshop.alarm.notify.AlarmReceiver
import com.homeshop.alarm.notify.AlarmScheduler
import com.homeshop.alarm.work.SyncWorker

/**
 * 수동 의존성 컨테이너. 1차 버전은 Hilt 없이 시작해 빌드 설정을 단순하게 유지한다.
 * 모듈이 늘어나면 Hilt 로 옮기면 된다 (claude-android-skill 의 architecture.md 참고).
 */
class AppContainer(context: Context) {
    private val db = AppDatabase.create(context)
    val settingsRepository = SettingsRepository(context)
    val scheduleRepository = ScheduleRepository(ScheduleApi(BuildConfig.SCHEDULE_URL), db.scheduleDao())
    val alarmScheduler = AlarmScheduler(context, settingsRepository)
}

class HomeshopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmReceiver.createChannel(this)
        SyncWorker.schedulePeriodic(this)
    }
}
