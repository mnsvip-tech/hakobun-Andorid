package com.delivery.navigator.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.delivery.navigator.MainActivity
import com.delivery.navigator.R
import java.util.Calendar

const val CHANNEL_TIME_WINDOW = "hakobun_time_window"
const val CHANNEL_ANNOUNCEMENT = "hakobun_announcement"

const val PREF_NOTIFICATION = "hakobun_notification_prefs"
const val KEY_NOTIF_ENABLED = "notif_enabled"

// AlarmManager request codes per time window
const val ALARM_MORNING = 1001
const val ALARM_AFTERNOON = 1002
const val ALARM_EVENING = 1003
const val ALARM_ANNOUNCEMENT = 2001

fun createNotificationChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_TIME_WINDOW, "時間帯アラート", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "午前・午後・夜間の配達時間帯をお知らせします"
        }
    )
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_ANNOUNCEMENT, "運営からのお知らせ", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "運営からの重要なお知らせを配信します"
        }
    )
}

fun isNotificationEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREF_NOTIFICATION, Context.MODE_PRIVATE)
        .getBoolean(KEY_NOTIF_ENABLED, true)
}

fun setNotificationEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREF_NOTIFICATION, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply()
    if (enabled) {
        scheduleAllTimeWindowAlarms(context)
    } else {
        cancelAllAlarms(context)
    }
}

fun scheduleAllTimeWindowAlarms(context: Context) {
    if (!isNotificationEnabled(context)) return
    scheduleTimeWindowAlarm(context, 9, 0, ALARM_MORNING)
    scheduleTimeWindowAlarm(context, 14, 0, ALARM_AFTERNOON)
    scheduleTimeWindowAlarm(context, 18, 0, ALARM_EVENING)
}

private fun scheduleTimeWindowAlarm(context: Context, hour: Int, minute: Int, requestCode: Int) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val intent = Intent(context, TimeWindowReceiver::class.java).apply {
        putExtra("requestCode", requestCode)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    } catch (e: SecurityException) {
        // 権限なし時は何もしない
    }
}

fun cancelAllAlarms(context: Context) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    listOf(ALARM_MORNING, ALARM_AFTERNOON, ALARM_EVENING).forEach { code ->
        val intent = Intent(context, TimeWindowReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }
}

fun showAnnouncementNotification(context: Context, title: String, body: String) {
    if (!isNotificationEnabled(context)) return
    val nm = context.getSystemService(NotificationManager::class.java)
    val tapIntent = PendingIntent.getActivity(
        context, ALARM_ANNOUNCEMENT,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENT)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setContentIntent(tapIntent)
        .build()
    nm.notify(ALARM_ANNOUNCEMENT, notification)
}
