package com.homeshop.alarm.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homeshop.alarm.work.SyncWorker

/** 재부팅하면 알람이 모두 지워지므로 동기화를 다시 돌려 알람을 재예약한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncWorker.runNow(context)
    }
}
