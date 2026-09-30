package com.kalorije.app.data.backup

import android.util.Log
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import java.io.ByteArrayOutputStream

private const val TAG = "DrivePrevCopies"
private const val APP_DATA_FOLDER = "appDataFolder"
private const val PREV_MARKER = "_prev_"
const val PREV_COPY_KEEP_COUNT = 5

/**
 * Prethodne kopije backup fajla u Drive appDataFolder-u:
 * "<osnova>_prev_<epochMillis>.json". Čuva se najnovijih [PREV_COPY_KEEP_COUNT].
 */
object DrivePrevCopies {

    fun prevCopyName(mainFileName: String, epochMillis: Long): String =
        "${mainFileName.removeSuffix(".json")}$PREV_MARKER$epochMillis.json"

    /** Vreme iz imena prethodne kopije, ili null ako ime nije prethodna kopija ovog fajla. */
    fun prevCopyTimestamp(mainFileName: String, name: String): Long? {
        val prefix = "${mainFileName.removeSuffix(".json")}$PREV_MARKER"
        if (!name.startsWith(prefix) || !name.endsWith(".json")) return null
        return name.removePrefix(prefix).removeSuffix(".json").toLongOrNull()
    }

    /** Imena kopija koje treba obrisati — sve osim najnovijih [keep]. */
    fun namesToPrune(mainFileName: String, names: List<String>, keep: Int = PREV_COPY_KEEP_COUNT): List<String> =
        names.mapNotNull { name -> prevCopyTimestamp(mainFileName, name)?.let { it to name } }
            .sortedByDescending { it.first }
            .drop(keep)
            .map { it.second }

    /**
     * Kopira postojeći backup pre prepisivanja. Baca izuzetak ako kopija ne
     * uspe — pozivalac tada MORA da prekine backup.
     *
     * [minIntervalMs] > 0: preskače kopiju ako već postoji novija od tog
     * intervala (za automatski backup, da kopije pokriju dane a ne minute).
     */
    fun copyBeforeOverwrite(
        drive: Drive,
        fileId: String,
        mainFileName: String,
        minIntervalMs: Long = 0L,
        now: Long = System.currentTimeMillis(),
    ) {
        if (minIntervalMs > 0) {
            val newest = listPrevCopies(drive, mainFileName)
                .mapNotNull { prevCopyTimestamp(mainFileName, it.second) }
                .maxOrNull()
            if (newest != null && now - newest in 0 until minIntervalMs) return
        }

        val metadata = com.google.api.services.drive.model.File().apply {
            name = prevCopyName(mainFileName, now)
            parents = listOf(APP_DATA_FOLDER)
        }
        try {
            drive.files().copy(fileId, metadata).execute()
        } catch (copyError: Exception) {
            // Rezerva: preuzmi sadržaj i napravi novi fajl. Ako i ovo pukne,
            // izuzetak ide pozivaocu i backup se prekida.
            Log.w(TAG, "files.copy nije uspeo, pokušavam preuzimanje + kreiranje", copyError)
            val content = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(content)
            drive.files().create(
                metadata,
                ByteArrayContent("application/json", content.toByteArray()),
            ).execute()
        }
    }

    /** Briše višak prethodnih kopija. Greška ovde nije kobna — samo se loguje. */
    fun prune(drive: Drive, mainFileName: String) {
        try {
            val copies = listPrevCopies(drive, mainFileName)
            val toDelete = namesToPrune(mainFileName, copies.map { it.second }).toSet()
            copies.filter { it.second in toDelete }.forEach { (id, name) ->
                try {
                    drive.files().delete(id).execute()
                } catch (e: Exception) {
                    Log.w(TAG, "Neuspešno brisanje stare kopije $name", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Neuspešno čišćenje starih kopija", e)
        }
    }

    // Parovi (id, ime). Filtrira se i lokalno po tačnom obrascu imena.
    private fun listPrevCopies(drive: Drive, mainFileName: String): List<Pair<String, String>> {
        val prefix = "${mainFileName.removeSuffix(".json")}$PREV_MARKER"
        val result = drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name contains '$prefix' and trashed = false")
            .setFields("files(id, name)")
            .setPageSize(100)
            .execute()
        return result.files.orEmpty()
            .filter { it.id != null && it.name != null && prevCopyTimestamp(mainFileName, it.name) != null }
            .map { it.id to it.name }
    }
}
