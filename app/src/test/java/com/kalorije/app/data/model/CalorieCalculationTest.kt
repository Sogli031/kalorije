package com.kalorije.app.data.model

import com.kalorije.app.util.WeightNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Testovi kalorijskih kalkulacija.
 * Referentni profil: muškarac, 29 god, 188 cm, 75 kg, "Lako aktivan" (1.375).
 * BMR = 10*75 + 6.25*188 - 5*29 + 5 = 1785 kcal, TDEE = 1785 * 1.375 = 2454 kcal.
 */
class CalorieCalculationTest {

    private val base = UserProfile(
        heightCm = 188,
        weightKg = 75.0,
        age = 29,
        isMale = true,
        activityLevel = ActivityLevel.LIGHT,
        goalMode = GoalMode.MAINTENANCE,
        goalPaceKgPerWeek = 0.0
    )

    @Test
    fun bmrAndTdeeMatchMifflinStJeor() {
        assertEquals(1785.0, base.bmr, 0.01)
        assertEquals(2454.375, base.baseTdee, 0.01)
    }

    @Test
    fun femaleBmrUsesMinus161Constant() {
        val female = base.copy(isMale = false)
        assertEquals(1785.0 - 166.0, female.bmr, 0.01)
    }

    @Test
    fun bulkTargetsMatchRequestedPace() {
        // Svaki tempo mora dati offset = pace * 7700 / 7 iznad TDEE
        mapOf(0.25 to 275, 0.5 to 550, 1.0 to 1100).forEach { (pace, expectedOffset) ->
            val profile = base.copy(goalMode = GoalMode.BULK, goalPaceKgPerWeek = pace)
            assertEquals(expectedOffset, profile.goalCalorieOffset)
            assertEquals(
                (base.baseTdee + expectedOffset).roundToInt(),
                profile.targetCalories
            )
        }
    }

    @Test
    fun predictedChangeRoundTripsBackToRequestedPace() {
        listOf(0.25, 0.35, 0.5, 0.75, 1.0).forEach { pace ->
            val bulk = base.copy(goalMode = GoalMode.BULK, goalPaceKgPerWeek = pace)
            assertEquals(pace, bulk.predictedWeeklyChange, 0.01)

            val cut = base.copy(goalMode = GoalMode.CUT, goalPaceKgPerWeek = pace)
            // Cut na 1.0 kg/ned pada ispod kalorijskog poda pa prognoza tada nije -1.0
            if (!cut.isCalorieFloorApplied) {
                assertEquals(-pace, cut.predictedWeeklyChange, 0.01)
            }
        }
    }

    @Test
    fun negativePaceIsTreatedAsMagnitudeNotDirection() {
        val cut = base.copy(goalMode = GoalMode.CUT, goalPaceKgPerWeek = -0.5)
        assertTrue("CUT sa negativnim tempom mora ostati deficit", cut.goalCalorieOffset < 0)
    }

    @Test
    fun targetNeverFallsBelowBmrOrAbsoluteMinimum() {
        val aggressive = base.copy(goalMode = GoalMode.CUT, goalPaceKgPerWeek = 1.0)
        assertTrue(aggressive.isCalorieFloorApplied)
        assertEquals(aggressive.minimumCalories, aggressive.targetCalories)
        assertTrue(aggressive.targetCalories >= aggressive.bmr.roundToInt())

        val smallFemale = UserProfile(
            heightCm = 165, weightKg = 60.0, age = 30, isMale = false,
            activityLevel = ActivityLevel.LIGHT,
            goalMode = GoalMode.CUT, goalPaceKgPerWeek = 1.0
        )
        assertTrue(smallFemale.targetCalories >= 1200)
        assertTrue(smallFemale.targetCalories >= smallFemale.bmr.roundToInt())
    }

    @Test
    fun trtMultiplierIsContinuousAcrossWeekFour() {
        fun multiplierAtDay(day: Long): Double = base.copy(
            useTrt = true,
            trtStartDate = LocalDate.now().minusDays(day)
        ).trtMultiplier

        assertEquals(1.0, multiplierAtDay(0), 0.001)
        assertEquals(1.05, multiplierAtDay(14), 0.001)
        assertEquals(1.10, multiplierAtDay(28), 0.001)
        assertEquals(1.10, multiplierAtDay(60), 0.001)
        // Nema skoka između dana 27 i 29
        assertTrue(abs(multiplierAtDay(29) - multiplierAtDay(27)) < 0.01)
    }

    // --- TDEE učenje ---

    private fun history(startWeight: Double, dailyChange: Double, days: Int): List<WeightEntry> {
        val start = LocalDate.now().minusDays(days.toLong() - 1)
        return (0 until days).map { i ->
            WeightEntry(date = start.plusDays(i.toLong()), weightKg = startWeight + dailyChange * i)
        }
    }

    @Test
    fun shortHistoryDoesNotLearnTdee() {
        // 7 dana: rolling prozori se potpuno preklapaju — ranije je davalo lažnu
        // korekciju "TDEE = koliko jedeš"
        val profile = base.copy(weightHistory = history(75.0, 0.02, 8))
        val learned = profile.calculateLearnedTdeeAdjustment(3000.0)
        assertEquals(0, learned.effectiveTdeeAdjustment)
    }

    @Test
    fun stableWeightOnSurplusRaisesLearnedTdee() {
        // 28 dana potpuno stabilne težine uz unos od 3000 kcal (formula kaže 2454)
        // → stvarni TDEE je viši, korekcija mora biti pozitivna
        val profile = base.copy(weightHistory = history(75.0, 0.0, 28))
        val learned = profile.calculateLearnedTdeeAdjustment(3000.0)
        assertTrue(
            "Očekivana pozitivna korekcija, dobijeno ${learned.effectiveTdeeAdjustment}",
            learned.effectiveTdeeAdjustment > 100
        )
    }

    @Test
    fun learnedTdeeRecoversKnownRate() {
        // Unos 3000 kcal, stvarni dobitak 0.5 kg/ned (0.0714 kg/dan) ≈ 550 kcal viška
        // → stvarni TDEE ≈ 2450, tj. korekcija blizu 0 u odnosu na formulu (2454)
        val profile = base.copy(weightHistory = history(75.0, 0.5 / 7.0, 35))
        val learned = profile.calculateLearnedTdeeAdjustment(3000.0)
        // Prvi prolaz je EMA (30% novog signala) pa se očekuje mala korekcija
        assertTrue(
            "Korekcija ${learned.effectiveTdeeAdjustment} je prevelika za tačan unos",
            abs(learned.effectiveTdeeAdjustment) < 60
        )
    }

    @Test
    fun sameDayWeightEntryReplacesPrevious() {
        val today = LocalDate.now()
        val profile = base
            .addWeightEntry(WeightEntry(date = today, weightKg = 80.0))
            .addWeightEntry(WeightEntry(date = today, weightKg = 79.5))
        assertEquals(1, profile.weightHistory.count { it.date == today })
        assertEquals(79.5, profile.currentWeight, 0.001)
    }

    // --- Ciklus: frakcione nedelje ---

    @Test
    fun cycleWeeklyRateUsesFractionalWeeks() {
        val cycle = CycleHistory(
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 21), // 20 dana = 2.857 nedelja
            startWeight = 80.0,
            endWeight = 82.0,
            avgDailyCalories = 3000
        )
        assertEquals(2.0 / (20 / 7.0), cycle.weeklyWeightChange, 0.001)
        assertEquals(0.7, cycle.weeklyWeightChange, 0.01)
    }

    @Test
    fun liveCycleWeeklyRateUsesFractionalWeeks() {
        val stats = LiveCycleStats(
            weeksOnCycle = 3, // 1-based prikaz za dan 14
            daysOnCycle = 14,
            startWeight = 80.0,
            currentWeight = 82.0,
            avgDailyCalories = 3000,
            isWaterRetentionPhase = true
        )
        // 2 kg za tačno 2 nedelje = 1.0 kg/ned (ranije se delilo sa 3)
        assertEquals(1.0, stats.weeklyWeightChange, 0.001)
    }

    @Test
    fun waterRetentionSaturatesNearPlateau() {
        val week8 = WeightNormalizer.estimateWaterRetention(8.0, 400.0)
        val week1 = WeightNormalizer.estimateWaterRetention(1.0, 400.0)
        val max = 0.5 + 4.0 * 0.6
        assertTrue(week1 < week8)
        assertEquals(max, week8, 0.1)
        assertFalse(week8 > max)
    }
}
