package com.kalorije.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import com.kalorije.app.BuildConfig
import com.kalorije.app.KalorijeApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ReminderScheduling"
private const val PROFILE_LOAD_TIMEOUT_MS = 8_000L

/**
 * Zakazuje podsetnik prema SAČUVANOM profilu.
 *
 * Profil se učitava asinhrono: čitanje `profile.value` odmah po kreiranju
 * repozitorijuma daje default profil (podsetnik isključen), pa alarm ne bi bio
 * zakazan posle restarta uređaja, hladnog starta ili restore-a. Zato se ovde
 * prvo čeka da se učitavanje završi.
 */
suspend fun scheduleReminderFromSavedProfile(context: Context) {
    val app = context.applicationContext as? KalorijeApplication
    if (app == null) {
        Log.w(TAG, "Application nije KalorijeApplication - podsetnik nije zakazan")
        return
    }
    val repository = app.foodRepository
    val loaded = withTimeoutOrNull(PROFILE_LOAD_TIMEOUT_MS) { repository.awaitLoaded() }
    if (loaded == null) {
        Log.w(TAG, "Profil nije učitan na vreme - podsetnik nije zakazan")
        return
    }

    val profile = repository.profile.value
    if (profile.weightReminderEnabled) {
        NotificationHelper.scheduleDailyReminder(
            context,
            profile.weightReminderHour,
            profile.weightReminderMinute
        )
        if (BuildConfig.DEBUG) Log.d(TAG, "Podsetnik zakazan za ${profile.weightReminderHour}:${profile.weightReminderMinute}")
    }
}

/** Isto, iz BroadcastReceiver-a: goAsync() drži proces živim dok se profil ne učita. */
fun BroadcastReceiver.scheduleReminderFromSavedProfileAsync(context: Context) {
    val app = context.applicationContext as? KalorijeApplication ?: return
    val pendingResult = goAsync()
    app.applicationScope.launch {
        try {
            scheduleReminderFromSavedProfile(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri zakazivanju podsetnika: ${e.message}")
        } finally {
            pendingResult.finish()
        }
    }
}
