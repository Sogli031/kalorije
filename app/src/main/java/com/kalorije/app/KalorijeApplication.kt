package com.kalorije.app

import android.app.Application
import com.kalorije.app.data.backup.BackupScheduler
import com.kalorije.app.data.backup.GoogleDriveBackupProvider
import com.kalorije.app.data.backup.SyncManager
import com.kalorije.app.data.repository.FoodRepository
import com.programi.crashlog.CrashLogStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KalorijeApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val foodRepository by lazy { FoodRepository(this) }
    val googleDriveBackup by lazy { GoogleDriveBackupProvider.get(this) }
    val syncManager by lazy { SyncManager(googleDriveBackup, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        CrashLogStorage.install(this, getString(R.string.app_name))

        // Dnevni backup ako korisnik dugo ne menja ništa (KEEP: ne resetuje se na svakom startu)
        BackupScheduler.schedulePeriodic(this)
    }
}
