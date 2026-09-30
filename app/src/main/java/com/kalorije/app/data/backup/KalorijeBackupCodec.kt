package com.kalorije.app.data.backup

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.kalorije.app.data.model.DailyGoals
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.MealType
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.util.GsonProvider
import java.lang.reflect.Type

/**
 * Sadržaj jednog backup-a. Sekcije koje su `null` ne postoje u backup-u
 * (stariji format) — pri vraćanju se tada zadržava lokalna vrednost.
 */
data class BackupSnapshot(
    val entries: List<FoodEntry>,
    val goals: DailyGoals?,
    val profile: UserProfile?,
    val savedFoods: List<SavedFood>?,
    val shakeOrder: List<String>?
)

/** Backup je neispravan — poruka je namenjena korisniku. */
class BackupFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Kodiranje/dekodiranje backup JSON-a. Dekodiranje je sve-ili-ništa: sekcija
 * koja postoji a ne može da se pročita je tvrda greška — nikad se ne pretvara
 * tiho u praznu listu, jer bi restore tada obrisao podatke i prijavio uspeh.
 */
object KalorijeBackupCodec {
    private val gson = GsonProvider.instance
    private val FOOD_ENTRY_LIST_TYPE: Type = object : TypeToken<List<FoodEntry>>() {}.type
    private val SAVED_FOOD_LIST_TYPE: Type = object : TypeToken<List<SavedFood>>() {}.type
    private val STRING_LIST_TYPE: Type = object : TypeToken<List<String>>() {}.type

    val knownKeys = setOf("entries", "goals", "profile", "savedFoods", "shakeOrder")

    fun encode(snapshot: BackupSnapshot): String {
        val root = JsonObject()
        root.add("entries", gson.toJsonTree(snapshot.entries, FOOD_ENTRY_LIST_TYPE))
        snapshot.goals?.let { root.add("goals", gson.toJsonTree(it)) }
        snapshot.profile?.let { root.add("profile", gson.toJsonTree(it)) }
        snapshot.savedFoods?.let { root.add("savedFoods", gson.toJsonTree(it, SAVED_FOOD_LIST_TYPE)) }
        snapshot.shakeOrder?.let { root.add("shakeOrder", gson.toJsonTree(it, STRING_LIST_TYPE)) }
        return gson.toJson(root)
    }

    fun decode(raw: String): BackupSnapshot {
        if (raw.isBlank()) throw BackupFormatException("Backup je prazan")

        val root: JsonObject = try {
            gson.fromJson(raw, JsonObject::class.java)
        } catch (e: Exception) {
            throw BackupFormatException("Backup nije ispravan JSON", e)
        } ?: throw BackupFormatException("Backup je prazan")

        if (root.keySet().intersect(knownKeys).isEmpty()) {
            throw BackupFormatException("Backup ne sadrži podatke aplikacije")
        }

        val entriesElement = root.section("entries")
            ?: throw BackupFormatException("Backup je nepotpun: nedostaju unosi hrane")

        return BackupSnapshot(
            entries = parseEntries(entriesElement),
            goals = root.section("goals")?.let { parseSection("Dnevni ciljevi") { gson.fromJson(it, DailyGoals::class.java) } },
            profile = root.section("profile")?.let { parseProfile(it) },
            savedFoods = root.section("savedFoods")?.let { parseSavedFoods(it) },
            shakeOrder = root.section("shakeOrder")?.let { parseShakeOrder(it) }
        )
    }

    private fun JsonObject.section(name: String): JsonElement? =
        get(name)?.takeUnless { it.isJsonNull }

    private fun <T> parseSection(label: String, block: () -> T?): T =
        try {
            block()
        } catch (e: Exception) {
            throw BackupFormatException("$label u backup-u nisu čitljivi", e)
        } ?: throw BackupFormatException("$label u backup-u nisu čitljivi")

    // Gson pravi objekte preko Unsafe-a, pa non-null Kotlin polja mogu biti null
    // ako ih u JSON-u nema. Strukturno neispravan zapis je greška; vrednosti se
    // NE filtriraju — vraća se tačno ono što je bilo u aplikaciji.
    private fun parseEntries(element: JsonElement): List<FoodEntry> {
        if (!element.isJsonArray) throw BackupFormatException("Unosi hrane u backup-u nisu čitljivi")
        val parsed: List<FoodEntry?> = parseSection("Unosi hrane") { gson.fromJson(element, FOOD_ENTRY_LIST_TYPE) }
        return parsed.mapIndexed { index, entry ->
            if (entry == null || entry.id.unsafeNullable().isNullOrBlank() || entry.date.unsafeNullable() == null ||
                entry.product.unsafeNullable() == null || entry.product.name.unsafeNullable() == null
            ) {
                throw BackupFormatException("Unos hrane #${index + 1} u backup-u je neispravan")
            }
            // Stari backupi: mealType/createdAt mogu da nedostaju.
            entry.copy(
                mealType = entry.mealType.unsafeNullable() ?: MealType.SNACK,
                createdAt = entry.createdAt.unsafeNullable() ?: entry.date.atStartOfDay()
            )
        }
    }

    private fun parseSavedFoods(element: JsonElement): List<SavedFood> {
        if (!element.isJsonArray) throw BackupFormatException("Sačuvane namirnice u backup-u nisu čitljive")
        val parsed: List<SavedFood?> = try {
            gson.fromJson(element, SAVED_FOOD_LIST_TYPE)
        } catch (e: Exception) {
            throw BackupFormatException("Sačuvane namirnice u backup-u nisu čitljive", e)
        } ?: throw BackupFormatException("Sačuvane namirnice u backup-u nisu čitljive")
        return parsed.mapIndexed { index, food ->
            if (food == null || food.id.unsafeNullable().isNullOrBlank() || food.name.unsafeNullable() == null ||
                food.createdAt.unsafeNullable() == null) {
                throw BackupFormatException("Sačuvana namirnica #${index + 1} u backup-u je neispravna")
            }
            food
        }
    }

    private fun parseProfile(element: JsonElement): UserProfile {
        val profile: UserProfile = try {
            gson.fromJson(element, UserProfile::class.java)
        } catch (e: Exception) {
            throw BackupFormatException("Profil u backup-u nije čitljiv", e)
        } ?: throw BackupFormatException("Profil u backup-u nije čitljiv")

        if (profile.heightCm <= 0 || profile.weightKg <= 0 || profile.age <= 0) {
            throw BackupFormatException("Profil u backup-u sadrži neispravne vrednosti")
        }
        if (profile.weightHistory.unsafeNullable() == null ||
            profile.weightHistory.any { it.unsafeNullable() == null || it.date.unsafeNullable() == null }) {
            throw BackupFormatException("Istorija težine u backup-u je neispravna")
        }
        if (profile.cycleHistory.unsafeNullable() == null ||
            profile.cycleHistory.any {
                it.unsafeNullable() == null || it.startDate.unsafeNullable() == null || it.endDate.unsafeNullable() == null
            }
        ) {
            throw BackupFormatException("Istorija ciklusa u backup-u je neispravna")
        }
        if (profile.activityLevel.unsafeNullable() == null || profile.goal.unsafeNullable() == null ||
            profile.goalMode.unsafeNullable() == null || profile.naturalTdeeProfile.unsafeNullable() == null ||
            profile.trtTdeeProfile.unsafeNullable() == null
        ) {
            throw BackupFormatException("Profil u backup-u je nepotpun")
        }
        return profile
    }

    private fun parseShakeOrder(element: JsonElement): List<String> {
        if (!element.isJsonArray) throw BackupFormatException("Šejk lista u backup-u nije čitljiva")
        val parsed: List<String?> = try {
            gson.fromJson(element, STRING_LIST_TYPE)
        } catch (e: Exception) {
            throw BackupFormatException("Šejk lista u backup-u nije čitljiva", e)
        } ?: throw BackupFormatException("Šejk lista u backup-u nije čitljiva")
        if (parsed.any { it == null }) throw BackupFormatException("Šejk lista u backup-u nije čitljiva")
        return parsed.filterNotNull().distinct()
    }
}

// Gson (Unsafe) ume da ostavi null u non-null Kotlin polju. Kroz generičku funkciju
// kompajler ne može da pretpostavi da vrednost nije null, pa je provera stvarna.
private fun <T> T.unsafeNullable(): T? = this
