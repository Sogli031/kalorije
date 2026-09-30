package com.kalorije.app.data.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.kalorije.app.BuildConfig

private const val TAG = "GoogleDriveBackup"
private const val BACKUP_FILE_NAME = "kalorije_backup.json"
private const val TEMP_BACKUP_FILE_NAME = "kalorije_backup_temp.json"
private const val APP_DATA_FOLDER = "appDataFolder"
private const val PREFS_NAME = "kalorije_backup_prefs"
private const val KEY_LAST_SYNC_TIME = "last_sync_time"
private const val KEY_CLOUD_ADOPTED = "cloud_adopted"
private const val EMPTY_DATA_THRESHOLD_LENGTH = 50

class GoogleDriveBackup(private val context: Context) {

    private val _signInState = MutableStateFlow<SignInState>(SignInState.SignedOut)
    val signInState: StateFlow<SignInState> = _signInState

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime

    private var driveService: Drive? = null

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        GoogleSignIn.getClient(context, signInOptions)
    }

    init {
        val savedTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        if (savedTime > 0) {
            _lastSyncTime.value = savedTime
        }

        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            _signInState.value = SignInState.SignedIn(account.email ?: "Unknown")
            initDriveService(account)
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            _signInState.value = SignInState.SignedIn(account.email ?: "Unknown")
            initDriveService(account)
            if (BuildConfig.DEBUG) Log.d(TAG, "Signed in as ${account.email}")
        } else {
            _signInState.value = SignInState.SignedOut
            if (BuildConfig.DEBUG) Log.e(TAG, "Sign in failed")
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut()
                _signInState.value = SignInState.SignedOut
                driveService = null
                if (BuildConfig.DEBUG) Log.d(TAG, "Signed out")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Sign out failed", e)
            }
        }
    }

    private fun initDriveService(account: GoogleSignInAccount) {
        if (BuildConfig.DEBUG) Log.d(TAG, "initDriveService: account=${account.email}")

        if (account.account == null) {
            Log.e(TAG, "initDriveService: account.account je null - Drive servis neće biti inicijalizovan")
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Kalorije")
            .build()

        if (BuildConfig.DEBUG) Log.d(TAG, "initDriveService: driveService created successfully")
    }

    /**
     * Proverava da li JSON sadrzi podatke za backup.
     * Vraca true ako su podaci prazni (nema sta da se backup-uje).
     */
    private fun isDataEmpty(jsonData: String): Boolean = isBackupPayloadEmpty(jsonData)

    /**
     * Da li je ova instalacija "preuzela" oblak: uspešan restore, uspešan backup
     * kad u oblaku nije bilo fajla, ili izričita potvrda korisnika.
     * Migracija postojećih instalacija: ko je već sinhronizovao, smatra se preuzetim.
     */
    fun isCloudAdopted(): Boolean {
        if (!prefs.contains(KEY_CLOUD_ADOPTED)) {
            val adopted = prefs.getLong(KEY_LAST_SYNC_TIME, 0L) > 0
            prefs.edit().putBoolean(KEY_CLOUD_ADOPTED, adopted).apply()
            return adopted
        }
        return prefs.getBoolean(KEY_CLOUD_ADOPTED, false)
    }

    fun markCloudAdopted() {
        prefs.edit().putBoolean(KEY_CLOUD_ADOPTED, true).apply()
    }

    /**
     * Da li u oblaku postoji backup. Greška pri listanju je [CloudCheck.Failed],
     * nikad [CloudCheck.Missing].
     */
    suspend fun checkCloudBackup(): CloudCheck {
        val drive = driveService ?: return CloudCheck.Failed("Niste prijavljeni na Google")
        return withContext(Dispatchers.IO) {
            try {
                if (findBackupFile(drive) != null) CloudCheck.Exists else CloudCheck.Missing
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "checkCloudBackup failed", e)
                CloudCheck.Failed(e.message ?: "Provera oblaka nije uspela")
            }
        }
    }

    /**
     * Javne metode za kontrolu statusa iz SyncManager-a.
     */
    fun updateLastSyncTime(timestamp: Long) {
        _lastSyncTime.value = timestamp
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
    }

    fun updateSyncState(state: SyncState) {
        _syncState.value = state
    }

    /**
     * Backup u oblak. Pravilo: prazno ne gazi popunjeno.
     *
     * Redosled je takav da oblak NIKAD ne ostane bez fajla:
     *   1. upload u temp fajl
     *   2. kopija starog backup-a u "kalorije_backup_prev_<vreme>.json"
     *      (ako kopija ne uspe, backup se prekida; stari fajl je netaknut)
     *   3. rename temp -> finalni naziv (od ovog trenutka novi backup važi)
     *   4. tek na kraju brisanje starog fajla
     * Temp se briše samo dok rename NIJE urađen — tada je stari fajl još tu.
     *
     * Dok ova instalacija nije preuzela oblak ([isCloudAdopted]), a u oblaku
     * već postoji backup, ništa se ne šalje ([BackupOutcome.CloudNotAdopted]).
     * Neuspelo listanje nikad se ne tumači kao "oblak je prazan".
     * SyncManager je odgovoran za pozivanje updateLastSyncTime() i updateSyncState().
     */
    suspend fun backupAtomic(jsonData: String): BackupOutcome {
        val drive = driveService
        if (drive == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "backupAtomic failed - driveService is null")
            return BackupOutcome.NotSignedIn
        }

        // Proveri da li ima podataka za backup
        if (isDataEmpty(jsonData)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "backupAtomic skipped - no data")
            return BackupOutcome.EmptyData
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "backupAtomic start, json length: ${jsonData.length}")

        return withContext(Dispatchers.IO) {
            var tempFileId: String? = null
            try {
                // Baca izuzetak ako listanje ne uspe — greška, ne "nema fajla".
                val oldFileIds = findBackupFiles(drive)
                if (oldFileIds.isNotEmpty() && !isCloudAdopted()) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "backupAtomic skipped - cloud backup not adopted on this install")
                    return@withContext BackupOutcome.CloudNotAdopted
                }

                deleteStaleTempFiles(drive)

                // Korak 1: Upload u temp fajl
                val tempMetadata = com.google.api.services.drive.model.File().apply {
                    name = TEMP_BACKUP_FILE_NAME
                    parents = listOf(APP_DATA_FOLDER)
                }
                val contentStream = ByteArrayContent.fromString("application/json", jsonData)
                val tempFile = drive.files().create(tempMetadata, contentStream).execute()
                tempFileId = tempFile.id
                if (BuildConfig.DEBUG) Log.d(TAG, "backupAtomic: temp file created, id=$tempFileId")

                // Korak 2: Kopija starog backup-a (najnovijeg) pre zamene
                oldFileIds.firstOrNull()?.let { newestOldId ->
                    DrivePrevCopies.copyBeforeOverwrite(drive, newestOldId, BACKUP_FILE_NAME)
                }

                // Korak 3: Rename temp fajl u finalni naziv
                val renameMetadata = com.google.api.services.drive.model.File().apply {
                    name = BACKUP_FILE_NAME
                }
                drive.files().update(tempFileId, renameMetadata).execute()
                if (BuildConfig.DEBUG) Log.d(TAG, "backupAtomic: temp renamed to $BACKUP_FILE_NAME")
                tempFileId = null // Novi backup važi — temp se više ne sme brisati

                // Korak 4: Tek sad obriši stari fajl. Neuspeh nije kobno: restore
                // uzima najnoviji fajl, a sledeći backup ponovo čisti.
                oldFileIds.forEach { oldId ->
                    try {
                        drive.files().delete(oldId).execute()
                        if (BuildConfig.DEBUG) Log.d(TAG, "backupAtomic: old file deleted, id=$oldId")
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "backupAtomic: brisanje starog fajla nije uspelo: $oldId", e)
                    }
                }
                DrivePrevCopies.prune(drive, BACKUP_FILE_NAME)

                // Uspešan backup (nov fajl ili svesno zamenjen) = oblak je naš.
                markCloudAdopted()
                BackupOutcome.Success
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "backupAtomic failed", e)
                BackupOutcome.Error(e.message ?: "Backup nije uspeo")
            } finally {
                // Cleanup: temp postoji samo ako rename nije urađen — stari fajl je tada netaknut.
                if (tempFileId != null) {
                    try {
                        drive.files().delete(tempFileId).execute()
                        if (BuildConfig.DEBUG) Log.d(TAG, "backupAtomic: cleanup temp file $tempFileId")
                    } catch (cleanupEx: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "backupAtomic: cleanup failed", cleanupEx)
                    }
                }
            }
        }
    }

    /**
     * Preuzima backup iz oblaka. Nikad ništa ne briše.
     */
    suspend fun restore(): RestoreDownload {
        val drive = driveService
        if (drive == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Restore failed - driveService is null (not signed in?)")
            _syncState.value = SyncState.Error("Niste prijavljeni na Google")
            return RestoreDownload.NotSignedIn
        }

        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing

                val fileId = findBackupFile(drive)
                if (fileId == null) {
                    _syncState.value = SyncState.Error("Nema backup fajla na cloud-u")
                    if (BuildConfig.DEBUG) Log.d(TAG, "No backup found")
                    return@withContext RestoreDownload.NoBackup
                }

                val outputStream = ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)

                val data = outputStream.toString("UTF-8")
                if (BuildConfig.DEBUG) Log.d(TAG, "Restore completed, data length: ${data.length}")
                _syncState.value = SyncState.Success
                if (data.isBlank()) RestoreDownload.NoBackup else RestoreDownload.Data(data)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Restore failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Restore failed")
                RestoreDownload.Error(e.message ?: "Preuzimanje nije uspelo")
            }
        }
    }

    // Najnoviji fajl sa TAČNIM imenom glavnog backup-a. Ništa ne briše.
    // Baca izuzetak ako listanje ne uspe.
    private fun findBackupFile(drive: Drive): String? = findBackupFiles(drive).firstOrNull()

    // Svi fajlovi sa tačnim imenom glavnog backup-a, najnoviji prvi. Prethodne
    // kopije ("..._prev_...") i temp fajl se ne poklapaju.
    private fun findBackupFiles(drive: Drive): List<String> {
        val result = drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
            .setOrderBy("modifiedTime desc")
            .setFields("files(id, name, modifiedTime)")
            .execute()
        if (BuildConfig.DEBUG) Log.d(TAG, "findBackupFiles: found ${result.files?.size ?: 0} files")

        return result.files.orEmpty()
            .filter { it.name == BACKUP_FILE_NAME }
            .mapNotNull { it.id }
    }

    // Temp fajlovi zaostali od prekinutog backup-a (samo tačno temp ime).
    private fun deleteStaleTempFiles(drive: Drive) {
        try {
            val result = drive.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name = '$TEMP_BACKUP_FILE_NAME' and trashed = false")
                .setFields("files(id, name)")
                .execute()
            result.files.orEmpty()
                .filter { it.name == TEMP_BACKUP_FILE_NAME }
                .forEach { stale -> stale.id?.let { drive.files().delete(it).execute() } }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Čišćenje starih temp fajlova nije uspelo", e)
        }
    }

    fun isSignedIn(): Boolean = _signInState.value is SignInState.SignedIn
}

/**
 * Bez ijednog unosa, sačuvanog jela, merenja težine ili završenog ciklusa nema šta
 * da se čuva. (Ranije je ručni backup ovu proveru zaobilazio sa force=true; sada
 * se ne zaobilazi, pa mora da prepozna i korisnika koji prati samo težinu.)
 */
internal fun isBackupPayloadEmpty(jsonData: String): Boolean {
    if (jsonData.length < EMPTY_DATA_THRESHOLD_LENGTH) return true
    return listOf("\"entries\":[{", "\"savedFoods\":[{", "\"weightHistory\":[{", "\"cycleHistory\":[{")
        .none { jsonData.contains(it) }
}

sealed class SignInState {
    data object SignedOut : SignInState()
    data class SignedIn(val email: String) : SignInState()
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data object Success : SyncState()
    data object EmptyData : SyncState()
    data class Error(val message: String) : SyncState()
}

sealed class BackupOutcome {
    data object Success : BackupOutcome()
    data object EmptyData : BackupOutcome()
    data object NotSignedIn : BackupOutcome()
    /** U oblaku postoji backup koji ova instalacija još nije preuzela — ništa nije poslato. */
    data object CloudNotAdopted : BackupOutcome()
    data class Error(val message: String) : BackupOutcome()
}

sealed class RestoreDownload {
    data class Data(val json: String) : RestoreDownload()
    data object NoBackup : RestoreDownload()
    data object NotSignedIn : RestoreDownload()
    data class Error(val message: String) : RestoreDownload()
}

sealed class CloudCheck {
    data object Exists : CloudCheck()
    data object Missing : CloudCheck()
    data class Failed(val message: String) : CloudCheck()
}
