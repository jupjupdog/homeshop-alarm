package com.homeshop.alarm.notify

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.homeshop.alarm.R
import com.homeshop.alarm.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AlarmReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission") // 위에서 직접 확인함
    override fun onReceive(context: Context, intent: Intent) {
        val broadcastKey = intent.getStringExtra(EXTRA_BROADCAST_KEY) ?: return
        val productKey = intent.getStringExtra(EXTRA_PRODUCT_KEY) ?: return
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        val start = intent.getLongExtra(EXTRA_START, 0L)
        val extra = intent.getIntExtra(EXTRA_COUNT, 0)

        // Android 13 미만에는 알림 런타임 권한이 없으므로 13 이상에서만 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, AlarmScheduler.requestCode(broadcastKey),
            MainActivity.detailIntent(context, productKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = TIME.format(Instant.ofEpochMilli(start))
        val text = if (extra > 0) "$title 외 ${extra}개" else title

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("[$category] $channel $time 방송 시작")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n눌러서 인터넷 최저가와 비교해 보세요."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(AlarmScheduler.requestCode(broadcastKey), notification)
    }

    companion object {
        const val CHANNEL_ID = "broadcast_alarm"
        private const val EXTRA_BROADCAST_KEY = "broadcastKey"
        private const val EXTRA_PRODUCT_KEY = "productKey"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_START = "start"
        private const val EXTRA_COUNT = "extraCount"
        private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))

        fun intent(
            context: Context, broadcastKey: String, productKey: String, channel: String,
            title: String, category: String, startMillis: Long, extraCount: Int,
        ) = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_BROADCAST_KEY, broadcastKey)
            .putExtra(EXTRA_PRODUCT_KEY, productKey)
            .putExtra(EXTRA_CHANNEL, channel)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_CATEGORY, category)
            .putExtra(EXTRA_START, startMillis)
            .putExtra(EXTRA_COUNT, extraCount)

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_broadcast),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_broadcast_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
