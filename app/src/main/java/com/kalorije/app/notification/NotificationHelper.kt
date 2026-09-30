package com.kalorije.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kalorije.app.BuildConfig
import com.kalorije.app.MainActivity
import com.kalorije.app.R
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val DAILY_REMINDER_FALLBACK_WINDOW_MILLIS = 10 * 60 * 1000L

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "weight_reminder_channel"
    private const val CHANNEL_NAME = "Podsetnik za merenje"
    private const val NOTIFICATION_ID = 1001
    private const val ALARM_REQUEST_CODE = 2001

    /**
     * Kreira notification channel (obavezno za Android 8+)
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Dnevni podsetnik da se izmeriš ujutru"
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager servis nije dostupan")
            return
        }
        notificationManager.createNotificationChannel(channel)
        if (BuildConfig.DEBUG) Log.d(TAG, "Notification channel kreiran")
    }

    /**
     * Prikazuje notifikaciju za merenje težine
     */
    fun showWeightReminderNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vreme za merenje!")
            .setContentText("Izmeri se pre doručka za najtačnije rezultate")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager servis nije dostupan - notifikacija nije prikazana")
            return
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
        if (BuildConfig.DEBUG) Log.d(TAG, "Notifikacija prikazana")
    }

    /**
     * Zakazuje dnevni alarm za podsetnik
     */
    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager servis nije dostupan - alarm nije zakazan")
            return
        }

        try {
            val intent = Intent(context, WeightReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = nextDailyReminderEpochMillis(hour = hour, minute = minute)
            scheduleDailyReminderAlarm(alarmManager, triggerAtMillis, pendingIntent)
            if (BuildConfig.DEBUG) Log.d(TAG, "Alarm zakazan za ${hour}:${minute}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Nema dozvolu za zakazivanje alarma: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri zakazivanju alarma: ${e.message}")
        }
    }

    /**
     * Otkazuje zakazani alarm
     */
    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager servis nije dostupan - alarm nije otkazan")
            return
        }

        try {
            val intent = Intent(context, WeightReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            if (BuildConfig.DEBUG) Log.d(TAG, "Alarm otkazan")
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri otkazivanju alarma: ${e.message}")
        }
    }
}

internal fun nextDailyReminderEpochMillis(
    now: LocalDateTime = LocalDateTime.now(),
    hour: Int,
    minute: Int,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val reminderTime = LocalTime.of(hour, minute)
    var reminderDateTime = LocalDateTime.of(now.toLocalDate(), reminderTime)
    if (reminderDateTime.isBefore(now)) {
        reminderDateTime = reminderDateTime.plusDays(1)
    }
    return reminderDateTime.atZone(zone).toInstant().toEpochMilli()
}

private fun scheduleDailyReminderAlarm(
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    pendingIntent: PendingIntent
) {
    if (canScheduleDailyReminderExactAlarms(alarmManager)) {
        val exactScheduled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }.isSuccess
        if (exactScheduled) return
    }
    alarmManager.setWindow(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        DAILY_REMINDER_FALLBACK_WINDOW_MILLIS,
        pendingIntent
    )
}

private fun canScheduleDailyReminderExactAlarms(alarmManager: AlarmManager): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return alarmManager.canScheduleExactAlarms()
}
