package com.kalorije.app.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kalorije.app.KalorijeApplication
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pozadinski posao koji šalje backup na Google Drive. Pokreće ga [BackupScheduler]
 * (posle izmene + dnevno), uz uslov da ima mreže.
 *
 * Pravila „prazno ne gazi popunjeno" i „sveža instalacija ne prepisuje zatečeni
 * backup" su u [GoogleDriveBackup.backupAtomic]; ovde se `markCloudAdopted` nikad
 * ne poziva, pa se takav backup tiho preskoči umesto da pita korisnika.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KalorijeApplication ?: return Result.success()

        if (!app.googleDriveBackup.isSignedIn()) {
            Log.d(TAG, "Preskačem auto-backup: korisnik nije prijavljen")
            return Result.success()
        }

        val repository = app.foodRepository
        // Bez učitanih podataka exportJsonForBackup baca izuzetak - bolje pokušati kasnije.
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) { repository.awaitLoaded(); true }
        if (loaded == null) {
            Log.w(TAG, "Podaci se još učitavaju - pokušavam kasnije")
            return Result.retry()
        }

        val outcome = app.syncManager.backup { repository.exportJsonForBackup() }
        return when (outcome) {
            BackupOutcome.Success -> {
                Log.d(TAG, "Auto-backup uspešan")
                Result.success()
            }
            // Sve tri su odluke koje ponavljanje ne menja.
            BackupOutcome.EmptyData,
            BackupOutcome.NotSignedIn,
            BackupOutcome.CloudNotAdopted -> {
                Log.d(TAG, "Preskačem auto-backup: $outcome")
                Result.success()
            }
            is BackupOutcome.Error -> {
                Log.w(TAG, "Auto-backup neuspešan: ${outcome.message}")
                if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val MAX_RETRY_ATTEMPTS = 7
        private const val LOAD_TIMEOUT_MS = 15_000L
    }
}
