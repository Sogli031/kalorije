package com.kalorije.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kalorije.app.BuildConfig

/**
 * BroadcastReceiver koji prima alarm i prikazuje notifikaciju.
 * Takođe ponovo zakazuje alarm za sledeći dan.
 */
class WeightReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Alarm primljen")

        // Prikaži notifikaciju
        NotificationHelper.showWeightReminderNotification(context)

        // Ponovo zakaži za sutra — tek kad se profil učita (alarm često budi
        // hladan proces, a tada je profil u memoriji još default)
        scheduleReminderFromSavedProfileAsync(context)
    }

    companion object {
        private const val TAG = "WeightReminderReceiver"
    }
}
