package com.kalorije.app.data.backup

import com.kalorije.app.data.model.ActivityLevel
import com.kalorije.app.data.model.CycleHistory
import com.kalorije.app.data.model.DailyGoals
import com.kalorije.app.data.model.FoodEntry
import com.kalorije.app.data.model.FoodProduct
import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.data.model.MealType
import com.kalorije.app.data.model.PostCycleState
import com.kalorije.app.data.model.SavedFood
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.data.model.WeightEntry
import com.kalorije.app.data.model.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class KalorijeBackupCodecTest {

    @Test
    fun `backup pa restore pa backup daje identican sadrzaj`() {
        val snapshot = populatedSnapshot()

        val exported = KalorijeBackupCodec.encode(snapshot)
        val restored = KalorijeBackupCodec.decode(exported)

        assertEquals(snapshot, restored)
        assertEquals(exported, KalorijeBackupCodec.encode(restored))
    }

    @Test
    fun `sejk lista i podesavanja podsetnika su deo backupa`() {
        val restored = KalorijeBackupCodec.decode(KalorijeBackupCodec.encode(populatedSnapshot()))

        assertEquals(listOf("food-2", "food-1"), restored.shakeOrder)
        assertTrue(restored.profile!!.weightReminderEnabled)
        assertEquals(6, restored.profile!!.weightReminderHour)
        assertEquals(45, restored.profile!!.weightReminderMinute)
        assertEquals(LocalDate.of(2026, 1, 3), restored.profile!!.weightPeriodStartDate)
    }

    @Test
    fun `stari backup bez novih kljuceva i sa makro poljima se i dalje vraca`() {
        // Format pre šejk liste: nema "shakeOrder", proizvodi nose stara makro polja,
        // unos nema mealType/createdAt.
        val raw = """
            {
              "entries": [
                {
                  "id": "e-1",
                  "date": "2025-11-02",
                  "product": {"name": "Pirinač", "caloriesPer100g": 130.0, "proteinPer100g": 2.7, "fatPer100g": 0.3, "carbsPer100g": 28.0},
                  "grams": 250.0
                }
              ],
              "goals": {"calories": 2800},
              "profile": {"heightCm": 188, "weightKg": 82.5, "age": 30, "isMale": true, "weightHistory": [{"date": "2025-11-01", "weightKg": 82.5}]},
              "savedFoods": [
                {"id": "food-1", "name": "Ovsene", "caloriesPer100g": 370.0, "defaultGrams": 80.0, "createdAt": "2025-10-01T08:00:00"}
              ]
            }
        """.trimIndent()

        val restored = KalorijeBackupCodec.decode(raw)

        assertEquals(1, restored.entries.size)
        assertEquals(MealType.SNACK, restored.entries.single().mealType)
        assertEquals(LocalDateTime.of(2025, 11, 2, 0, 0), restored.entries.single().createdAt)
        assertEquals(2800, restored.goals?.calories)
        assertEquals(82.5, restored.profile!!.weightKg, 0.001)
        assertEquals(1, restored.savedFoods?.size)
        assertNull(restored.shakeOrder)
    }

    @Test
    fun `necitljiva sekcija je tvrda greska a ne prazna lista`() {
        val good = KalorijeBackupCodec.encode(populatedSnapshot())

        val badDate = good.replace("\"date\":\"2026-03-01\"", "\"date\":\"nije-datum\"")
        assertTrue(badDate != good)
        assertTrue(runCatching { KalorijeBackupCodec.decode(badDate) }.exceptionOrNull() is BackupFormatException)

        val entriesNotArray = """{"entries": {"a": 1}, "savedFoods": []}"""
        assertTrue(runCatching { KalorijeBackupCodec.decode(entriesNotArray) }.exceptionOrNull() is BackupFormatException)

        val entryWithoutProduct = """{"entries": [{"id": "e-1", "date": "2026-03-01", "grams": 100.0}]}"""
        assertTrue(runCatching { KalorijeBackupCodec.decode(entryWithoutProduct) }.exceptionOrNull() is BackupFormatException)

        val badProfile = """{"entries": [], "profile": {"heightCm": 0, "weightKg": 80.0, "age": 30}}"""
        assertTrue(runCatching { KalorijeBackupCodec.decode(badProfile) }.exceptionOrNull() is BackupFormatException)
    }

    @Test
    fun `json koji nije backup aplikacije se odbija`() {
        assertTrue(runCatching { KalorijeBackupCodec.decode("") }.isFailure)
        assertTrue(runCatching { KalorijeBackupCodec.decode("nije json {{{") }.isFailure)
        assertTrue(runCatching { KalorijeBackupCodec.decode("""{"nesto": 1}""") }.isFailure)
        // Bez unosa hrane backup je nepotpun
        assertTrue(runCatching { KalorijeBackupCodec.decode("""{"savedFoods": []}""") }.isFailure)
    }

    @Test
    fun `prazno stanje se ne salje u oblak`() {
        val empty = KalorijeBackupCodec.encode(
            BackupSnapshot(
                entries = emptyList(),
                goals = DailyGoals(),
                profile = UserProfile(),
                savedFoods = emptyList(),
                shakeOrder = emptyList()
            )
        )

        assertTrue(isBackupPayloadEmpty(empty))
        assertFalse(isBackupPayloadEmpty(KalorijeBackupCodec.encode(populatedSnapshot())))

        // Korisnik koji prati samo težinu ima šta da sačuva
        val weightOnly = KalorijeBackupCodec.encode(
            BackupSnapshot(
                entries = emptyList(),
                goals = DailyGoals(),
                profile = UserProfile(weightHistory = listOf(WeightEntry(LocalDate.of(2026, 3, 1), 82.0))),
                savedFoods = emptyList(),
                shakeOrder = emptyList()
            )
        )
        assertFalse(isBackupPayloadEmpty(weightOnly))
    }

    private fun populatedSnapshot(): BackupSnapshot {
        val rice = FoodProduct(id = "7", name = "Pirinač", brand = null, caloriesPer100g = 130.0, servingSize = 50.0)
        val profile = UserProfile(
            heightCm = 188,
            weightKg = 82.35,
            age = 30,
            isMale = true,
            activityLevel = ActivityLevel.MODERATE,
            goal = WeightGoal.MAINTAIN,
            manualTdee = 3100,
            tdeeAdjustment = 120,
            weightHistory = listOf(
                WeightEntry(date = LocalDate.of(2026, 3, 1), weightKg = 82.35, time = "05:10"),
                WeightEntry(date = LocalDate.of(2026, 2, 28), weightKg = 82.1)
            ),
            weightPeriodStartDate = LocalDate.of(2026, 1, 3),
            goalWeight = 85.0,
            fixedStartingWeight = 78.0,
            useTrt = true,
            trtStartDate = LocalDate.of(2026, 1, 10),
            trtCycleEndDate = LocalDate.of(2026, 5, 1),
            trtDoseMgWeek = 250.0,
            usesAromataseInhibitor = true,
            cycleHistory = listOf(
                CycleHistory(
                    id = "cycle-1",
                    startDate = LocalDate.of(2025, 3, 1),
                    endDate = LocalDate.of(2025, 7, 1),
                    startWeight = 76.0,
                    endWeight = 81.0,
                    avgDailyCalories = 3300,
                    doseMgPerWeek = 300.0,
                    usedAi = false,
                    tdee = 3000
                )
            ),
            weightReminderEnabled = true,
            weightReminderHour = 6,
            weightReminderMinute = 45,
            resistanceTrainingDays = 4,
            postCycleState = PostCycleState(cycleEndDate = LocalDate.of(2025, 7, 1)),
            goalMode = GoalMode.BULK,
            goalPaceKgPerWeek = 0.35,
            goalMigrated = true
        )
        return BackupSnapshot(
            entries = listOf(
                FoodEntry(
                    id = "e-1",
                    date = LocalDate.of(2026, 3, 1),
                    createdAt = LocalDateTime.of(2026, 3, 1, 12, 30, 15),
                    product = rice,
                    grams = 250.0,
                    mealType = MealType.LUNCH,
                    groupId = "g-1",
                    time = "12:30"
                ),
                FoodEntry(
                    id = "e-2",
                    date = LocalDate.of(2026, 2, 28),
                    createdAt = LocalDateTime.of(2026, 2, 28, 8, 0),
                    product = FoodProduct(id = "9", name = "Jaja", brand = "Domaća", caloriesPer100g = 143.0),
                    grams = 120.0
                )
            ),
            goals = DailyGoals(calories = 3450),
            profile = profile,
            savedFoods = listOf(
                SavedFood(id = "food-1", name = "Ovsene", caloriesPer100g = 370.0, defaultGrams = 80.0, createdAt = LocalDateTime.of(2025, 10, 1, 8, 0)),
                SavedFood(id = "food-2", name = "Banana", caloriesPer100g = 89.0, defaultGrams = 120.0, createdAt = LocalDateTime.of(2025, 10, 2, 8, 0))
            ),
            shakeOrder = listOf("food-2", "food-1")
        )
    }
}
