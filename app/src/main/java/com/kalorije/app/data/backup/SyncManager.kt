package com.kalorije.app.data.backup

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kalorije.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlin.math.pow

private const val TAG = "SyncManager"

/**
 * Status sinhronizacije za UI.
 */
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Retrying(val attempt: Int, val maxAttempts: Int) : SyncStatus()
    data class Success(val timestamp: Long) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

/**
 * Manager za sekvencijalno izvršavanje sync operacija sa retry logikom i mutex lock-om.
 *
 * Backup i restore su obični suspend pozivi pod istim Mutex-om: nema reda
 * čekanja koji bi mogao da "proguta" zahtev (ranije je CONFLATED kanal mogao
 * da zameni Restore Backup-om, pa se restore nikad ne bi završio).
 * Exponential backoff za retry (1s, 2s, 4s, max 10s).
 */
class SyncManager(
    private val googleDriveBackup: GoogleDriveBackup,
    private val scope: CoroutineScope
) {

    private val syncMutex = Mutex()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val lastSyncTime: StateFlow<Long?> = googleDriveBackup.lastSyncTime
    val signInState: StateFlow<SignInState> = googleDriveBackup.signInState

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 10000L
    }

    /**
     * Izvršava backup sa retry logikom (do 3 pokušaja sa exponential backoff-om).
     *
     * [produceJson] se poziva unutar zaključane sekcije i sme da baci izuzetak
     * (npr. podaci još nisu učitani) — tada se ništa ne šalje.
     * Ponavlja se samo prava greška; "nema podataka", "niste prijavljeni" i
     * "oblak nije preuzet" se odmah vraćaju pozivaocu.
     */
    suspend fun backup(produceJson: () -> String): BackupOutcome = runInAppScope {
        syncMutex.withLock {
            val jsonData = try {
                produceJson()
            } catch (e: Exception) {
                val message = e.message ?: "Podaci nisu spremni za backup"
                _syncStatus.value = SyncStatus.Error(message)
                return@withLock BackupOutcome.Error(message)
            }

            var lastError = "Backup nije uspeo"
            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                _syncStatus.value = if (attempt == 1) {
                    SyncStatus.Syncing
                } else {
                    SyncStatus.Retrying(attempt, MAX_RETRY_ATTEMPTS)
                }

                val outcome = try {
                    googleDriveBackup.backupAtomic(jsonData)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Backup attempt $attempt failed", e)
                    BackupOutcome.Error(e.message ?: "Backup nije uspeo")
                }

                when (outcome) {
                    BackupOutcome.Success -> {
                        val now = System.currentTimeMillis()
                        googleDriveBackup.updateLastSyncTime(now)
                        _syncStatus.value = SyncStatus.Success(now)
                        if (BuildConfig.DEBUG) Log.d(TAG, "Backup successful on attempt $attempt")
                        return@withLock outcome
                    }
                    BackupOutcome.EmptyData -> {
                        _syncStatus.value = SyncStatus.Error("Nema podataka za backup")
                        return@withLock outcome
                    }
                    BackupOutcome.NotSignedIn -> {
                        _syncStatus.value = SyncStatus.Error("Niste prijavljeni na Google")
                        return@withLock outcome
                    }
                    BackupOutcome.CloudNotAdopted -> {
                        _syncStatus.value = SyncStatus.Idle
                        return@withLock outcome
                    }
                    is BackupOutcome.Error -> {
                        lastError = outcome.message
                        if (BuildConfig.DEBUG) Log.w(TAG, "Backup failed on attempt $attempt: $lastError")
                    }
                }

                // Ako nije poslednji pokušaj, čekaj pre sledećeg
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val delayMs = calculateBackoff(attempt)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Retrying in ${delayMs}ms")
                    delay(delayMs)
                }
            }

            // Svi pokušaji neuspešni
            _syncStatus.value = SyncStatus.Error("Backup nije uspeo nakon $MAX_RETRY_ATTEMPTS pokušaja")
            if (BuildConfig.DEBUG) Log.e(TAG, "Backup failed after all retries")
            BackupOutcome.Error(lastError)
        }
    }

    /**
     * Računa exponential backoff delay.
     */
    private fun calculateBackoff(attempt: Int): Long {
        return (INITIAL_RETRY_DELAY_MS * 2.0.pow(attempt - 1)).toLong()
            .coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    /**
     * Preuzima backup iz oblaka (čeka rezultat). Ne može da se "izgubi" iza backup-a.
     */
    suspend fun restore(): RestoreDownload = runInAppScope {
        syncMutex.withLock {
            _syncStatus.value = SyncStatus.Syncing
            val download = try {
                googleDriveBackup.restore()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Restore failed", e)
                RestoreDownload.Error(e.message ?: "Restore nije uspeo")
            }
            _syncStatus.value = when (download) {
                is RestoreDownload.Data -> SyncStatus.Success(System.currentTimeMillis())
                RestoreDownload.NoBackup -> SyncStatus.Error("Nema backup-a u oblaku")
                RestoreDownload.NotSignedIn -> SyncStatus.Error("Niste prijavljeni na Google")
                is RestoreDownload.Error -> SyncStatus.Error(download.message)
            }
            download
        }
    }

    /**
     * Pokreće blok u application scope-u: izlazak sa ekrana ne sme da prekine
     * backup ili restore na pola.
     */
    suspend fun <T> runInAppScope(block: suspend () -> T): T = scope.async { block() }.await()

    /** Status za grešku koja nastane posle preuzimanja (npr. neispravan backup). */
    fun reportRestoreFailure(message: String) {
        _syncStatus.value = SyncStatus.Error(message)
    }

    suspend fun checkCloudBackup(): CloudCheck = googleDriveBackup.checkCloudBackup()

    fun isCloudAdopted(): Boolean = googleDriveBackup.isCloudAdopted()

    fun markCloudAdopted() = googleDriveBackup.markCloudAdopted()

    /**
     * Proverava da li je sync manager idle (nema operacija u toku).
     */
    fun isIdle(): Boolean {
        return !syncMutex.isLocked && _syncStatus.value is SyncStatus.Idle
    }

    /**
     * Passthrough metode za Google Sign-In (delegiraju GoogleDriveBackup-u).
     */
    fun getSignInIntent() = googleDriveBackup.getSignInIntent()

    fun handleSignInResult(account: GoogleSignInAccount?) = googleDriveBackup.handleSignInResult(account)

    suspend fun signOut() = googleDriveBackup.signOut()
}
