package com.kalorije.app.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Zakazuje pozadinske backup-ove preko WorkManager-a:
 *  - [scheduleAfterChange]: posle izmene podataka; jedinstven posao sa odlaganjem i
 *    politikom REPLACE, pa se niz brzih izmena spoji u jedan backup.
 *  - [schedulePeriodic]: dnevni „safety-net" ako korisnik dugo ne menja ništa.
 *
 * Oba traže mrežu i koriste eksponencijalni backoff pri neuspehu. [BackupWorker]
 * sam preskače posao ako korisnik nije prijavljen ili bi backup pregazio oblak.
 */
object BackupScheduler {

    private const val AFTER_CHANGE_WORK = "kalorije_cloud_backup_after_change"
    private const val PERIODIC_WORK = "kalorije_cloud_backup_periodic"
    private const val DEBOUNCE_DELAY_MINUTES = 5L
    private const val PERIODIC_INTERVAL_HOURS = 24L

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun scheduleAfterChange(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(DEBOUNCE_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        // Zakazivanje ne sme da obori snimanje podataka: u okruzenju bez
        // inicijalizovanog WorkManager-a (npr. JVM testovi) ovo samo ne uradi nista.
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(AFTER_CHANGE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            PERIODIC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
