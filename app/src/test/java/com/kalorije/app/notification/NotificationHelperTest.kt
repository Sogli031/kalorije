package com.kalorije.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationHelperTest {
    private val zone = ZoneId.of("Europe/Belgrade")

    @Test
    fun `next daily reminder uses today when time has not passed`() {
        val now = LocalDateTime.of(2026, 5, 30, 6, 45)

        val triggerAt = nextDailyReminderEpochMillis(now, hour = 7, minute = 15, zone = zone)

        assertEquals(
            LocalDateTime.of(2026, 5, 30, 7, 15).atZone(zone).toInstant().toEpochMilli(),
            triggerAt
        )
    }

    @Test
    fun `next daily reminder moves to tomorrow when time has passed`() {
        val now = LocalDateTime.of(2026, 5, 30, 7, 16)

        val triggerAt = nextDailyReminderEpochMillis(now, hour = 7, minute = 15, zone = zone)

        assertEquals(
            LocalDateTime.of(2026, 5, 31, 7, 15).atZone(zone).toInstant().toEpochMilli(),
            triggerAt
        )
    }

    @Test
    fun `scheduler uses exact alarm with bounded fallback`() {
        val source = listOf(
            File("src/main/java/com/kalorije/app/notification/NotificationHelper.kt"),
            File("app/src/main/java/com/kalorije/app/notification/NotificationHelper.kt")
        ).first { it.exists() }.readText()

        assertTrue(source.contains("setExactAndAllowWhileIdle"))
        assertTrue(source.contains("DAILY_REMINDER_FALLBACK_WINDOW_MILLIS = 10 * 60 * 1000L"))
        assertFalse(source.contains("setAndAllowWhileIdle"))
    }

    @Test
    fun `boot receiver reschedules after app update and time changes`() {
        val receiver = listOf(
            File("src/main/java/com/kalorije/app/notification/BootReceiver.kt"),
            File("app/src/main/java/com/kalorije/app/notification/BootReceiver.kt")
        ).first { it.exists() }.readText()
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }.readText()

        assertTrue(receiver.contains("Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(receiver.contains("Intent.ACTION_TIME_CHANGED"))
        assertTrue(receiver.contains("Intent.ACTION_TIMEZONE_CHANGED"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(manifest.contains("android.intent.action.TIME_SET"))
        assertTrue(manifest.contains("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `main activity warns about exact alarm permission on every startup`() {
        val source = listOf(
            File("src/main/java/com/kalorije/app/MainActivity.kt"),
            File("app/src/main/java/com/kalorije/app/MainActivity.kt")
        ).first { it.exists() }.readText()

        assertTrue(source.contains("override fun onResume()"))
        assertTrue(source.contains("requestExactAlarmPermissionIfNeeded()"))
        assertTrue(source.contains("ExactAlarmPermissionDialog("))
        assertTrue(source.contains("mutableStateOf(false)"))
        assertTrue(source.contains("Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM"))
        assertFalse(source.contains("AlertDialog.Builder(this)"))
        assertFalse(source.contains("exact_alarm_permission_asked"))
    }
}
