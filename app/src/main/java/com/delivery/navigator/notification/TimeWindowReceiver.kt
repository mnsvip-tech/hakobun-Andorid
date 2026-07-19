package com.delivery.navigator.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.delivery.navigator.MainActivity
import com.delivery.navigator.data.LocalDeliveryStore
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.TimeWindow

class TimeWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isNotificationEnabled(context)) return

        val requestCode = intent.getIntExtra("requestCode", 0)
        val timeWindow = when (requestCode) {
            ALARM_MORNING -> TimeWindow.Morning
            ALARM_AFTERNOON -> TimeWindow.Afternoon
            ALARM_EVENING -> TimeWindow.Evening
            else -> return
        }

        val store = LocalDeliveryStore(context)
        val packages = store.loadPackages()
        val pending = packages.count {
            it.timeWindow == timeWindow && it.status == DeliveryStatus.Pending
        }

        if (pending == 0) return

        val label = timeWindow.label
        val title = "【HAKOBUN】${label}指定の配達があります"
        val body = "${label}指定の未配達荷物が${pending}件あります"

        val tapIntent = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TIME_WINDOW)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(requestCode, notification)
    }
}
