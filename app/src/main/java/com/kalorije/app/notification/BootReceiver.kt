package com.kalorije.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kalorije.app.BuildConfig

/**
 * BroadcastReceiver koji se poziva posle restarta uređaja.
 * Ponovo zakazuje alarm za podsetnik ako je uključen.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }

        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "Sistemska promena - proveravam podsetnik")

            // Kreiraj notification channel
            NotificationHelper.createNotificationChannel(context)

            // Proveri da li je podsetnik uključen i ponovo ga zakaži — tek kad se
            // profil učita sa diska (odmah posle starta procesa profil je još default)
            scheduleReminderFromSavedProfileAsync(context)
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri obradi boot eventa: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
