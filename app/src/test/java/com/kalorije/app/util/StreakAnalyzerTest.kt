package com.kalorije.app.util

import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.data.model.UserProfile
import com.kalorije.app.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakAnalyzerTest {

    private val today = LocalDate.of(2026, 8, 19)

    private fun profile(mode: GoalMode = GoalMode.BULK, pace: Double = 0.35) = UserProfile(
        goalMode = mode,
        goalPaceKgPerWeek = pace,
        manualTdee = 2500,
        customCalories = null
    )

    private fun a_tolerance(p: UserProfile) = StreakAnalyzer.toleranceFor(p.targetCalories)

    private fun days(start: LocalDate, vararg calories: Double): Map<LocalDate, Double> =
        calories.mapIndexed { i, c -> start.plusDays(i.toLong()) to c }.toMap()

    @Test
    fun `dan u toleranciji se broji kao pogodjen`() {
        val p = profile()
        val target = p.targetCalories
        val data = days(today.minusDays(2), target.toDouble(), target + 50.0, target - 50.0)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(3, a.onTargetDays)
        assertEquals(0, a.overDays)
        assertEquals(0, a.underDays)
        assertEquals(3, a.currentStreak)
    }

    @Test
    fun `neupisan dan prekida niz ali ne ulazi u odstupanje`() {
        val p = profile()
        val target = p.targetCalories.toDouble()
        val data = mapOf(
            today.minusDays(4) to target,
            today.minusDays(3) to target,
            // today-2 nedostaje
            today.minusDays(1) to target,
            today to target
        )

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(1, a.notLoggedDays)
        assertEquals(2, a.currentStreak)
        assertEquals(2, a.bestStreak)
        assertEquals(0.0, a.totalDeviationKcal, 0.001)
    }

    @Test
    fun `danas ispod cilja ne obara niz od juce`() {
        val p = profile()
        val target = p.targetCalories.toDouble()
        val data = days(today.minusDays(3), target, target, target, 200.0)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(3, a.currentStreak)
    }

    @Test
    fun `manjak u bulku racuna usporenje u danima`() {
        val p = profile(GoalMode.BULK, pace = 0.35)
        val target = p.targetCalories.toDouble()
        // 10 dana, svaki 500 kcal ispod cilja => -5000 kcal
        val data = days(today.minusDays(9), *DoubleArray(10) { target - 500 })

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(10, a.underDays)
        assertEquals(-5000.0, a.totalDeviationKcal, 0.001)
        assertEquals(-5000.0 / UserProfile.KCAL_PER_KG, a.deviationKg, 0.0001)
        // Manjak u bulku => pozitivno kašnjenje
        assertTrue("ocekivano usporenje, dobijeno ${a.delayDays}", (a.delayDays ?: 0.0) > 0)
    }

    @Test
    fun `visak u cutu racuna usporenje u danima`() {
        val p = profile(GoalMode.CUT, pace = 0.5)
        val target = p.targetCalories.toDouble()
        val data = days(today.minusDays(4), *DoubleArray(5) { target + 600 })

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(5, a.overDays)
        assertTrue("ocekivano usporenje, dobijeno ${a.delayDays}", (a.delayDays ?: 0.0) > 0)
    }

    @Test
    fun `odrzavanje nema kasnjenje`() {
        val p = profile(GoalMode.MAINTENANCE, pace = 0.0)
        val data = days(today.minusDays(2), 3000.0, 3000.0, 3000.0)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertNull(a.delayDays)
    }

    @Test
    fun `najduzi niz se nalazi i u sredini istorije`() {
        val p = profile()
        val target = p.targetCalories.toDouble()
        val data = days(
            today.minusDays(7),
            target, target, target, target, // 4 u cilju
            100.0,                          // prekid
            target, target,                 // 2 u cilju
            100.0
        )

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(4, a.bestStreak)
        // Danas je promasen, ali se danasnji dan preskace (jos traje),
        // pa se trenutni niz meri od juce unazad: 2 dana.
        assertEquals(2, a.currentStreak)
        assertEquals(2, a.missDays)
    }

    @Test
    fun `visak ne prekida niz u bulku`() {
        val p = profile(GoalMode.BULK)
        val target = p.targetCalories.toDouble()
        // Sredisnji dan je znatno iznad cilja — i dalje radi za gojenje.
        val data = days(today.minusDays(3), target, target + 900, target, target)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(1, a.overDays)
        assertEquals(0, a.missDays)
        assertEquals(4, a.hitDays)
        assertEquals(4, a.currentStreak)
        assertEquals(100, a.adherencePercent)
        // Tacno u toleranciju je palo 3 od 4 dana.
        assertEquals(75, a.exactTargetPercent)
    }

    @Test
    fun `manjak prekida niz u bulku`() {
        val p = profile(GoalMode.BULK)
        val target = p.targetCalories.toDouble()
        val data = days(today.minusDays(3), target, target - 900, target, target)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(1, a.missDays)
        assertEquals(2, a.currentStreak)
        assertEquals(1, a.worstSpans.size)
    }

    @Test
    fun `manjak ne prekida niz u cutu ali visak prekida`() {
        val p = profile(GoalMode.CUT, pace = 0.5)
        val target = p.targetCalories.toDouble()
        val data = days(today.minusDays(3), target - 900, target, target + 900, target)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(1, a.missDays)
        assertEquals(3, a.hitDays)
        // Visak je pretposlednji dan, pa je niz od tada samo poslednji dan.
        assertEquals(1, a.currentStreak)
    }

    @Test
    fun `kod odrzavanja oba smera promasuju`() {
        val p = profile(GoalMode.MAINTENANCE, pace = 0.0)
        val target = p.targetCalories.toDouble()
        val data = days(today.minusDays(2), target - 900, target, target + 900)

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertEquals(2, a.missDays)
        assertEquals(1, a.hitDays)
    }

    @Test
    fun `dnevna promena kg se racuna prema TDEE a ne prema cilju`() {
        val p = profile()
        val tdee = p.tdee // manualTdee = 2500
        // Dan sa 3000 kcal => +500 kcal iznad odrzavanja
        val data = mapOf(today.minusDays(1) to tdee + 500)

        val a = StreakAnalyzer.analyze(data, p, today = today)
        val day = a.days.single()

        assertEquals(500.0, day.energyBalance, 0.001)
        assertEquals(500.0 / UserProfile.KCAL_PER_KG, day.estimatedKgChange, 0.00001)
    }

    @Test
    fun `kompoziciona procena je veca od konvencije 7700`() {
        val p = profile()
        val data = mapOf(today.minusDays(1) to p.tdee + 500)

        val a = StreakAnalyzer.analyze(data, p, today = today)
        val day = a.days.single()

        // Nemasno tkivo je energetski jeftinije, pa isti visak pomera vise kilograma.
        assertTrue(
            "kompozicija ${day.estimatedKgChangeComposition} nije veca od ${day.estimatedKgChange}",
            day.estimatedKgChangeComposition > day.estimatedKgChange
        )
        // Gustina mora biti izmedju cistog nemasnog i cistog masnog tkiva.
        assertTrue(
            a.tissueKcalPerKg > StreakAnalyzer.LEAN_TISSUE_KCAL_PER_KG &&
                a.tissueKcalPerKg < StreakAnalyzer.FAT_TISSUE_KCAL_PER_KG
        )
    }

    @Test
    fun `neupisan dan nema procenu promene kg`() {
        val p = profile()
        val data = mapOf(
            today.minusDays(3) to p.tdee + 500,
            today.minusDays(1) to p.tdee + 500
        )

        val a = StreakAnalyzer.analyze(data, p, today = today)
        val missing = a.days.first { it.status == DayStatus.NOT_LOGGED }

        assertEquals(0.0, missing.energyBalance, 0.001)
        assertEquals(0.0, missing.estimatedKgChange, 0.00001)
        // Zbir preko svih dana broji samo dva upisana dana.
        assertEquals(1000.0 / UserProfile.KCAL_PER_KG, a.estimatedKgFromIntake, 0.00001)
    }

    @Test
    fun `veci udeo nemasne mase snizava cenu kilograma`() {
        val lean = UserProfile(manualTdee = 2500, weightKg = 70.0, useTrt = true, resistanceTrainingDays = 4)
        val heavy = UserProfile(manualTdee = 2500, weightKg = 110.0, useTrt = false, resistanceTrainingDays = 0)

        val leanDensity = StreakAnalyzer.tissueEnergyDensity(lean, 300).first
        val heavyDensity = StreakAnalyzer.tissueEnergyDensity(heavy, 300).first

        assertTrue("$leanDensity nije manje od $heavyDensity", leanDensity < heavyDensity)
    }

    @Test
    fun `ledger sabira sve prema odrzavanju`() {
        val p = profile(GoalMode.BULK)
        val tdee = p.tdee
        // 4 dana po +600 iznad odrzavanja, 1 dan -200 ispod
        val data = days(today.minusDays(4), tdee + 600, tdee + 600, tdee + 600, tdee + 600, tdee - 200)

        val a = StreakAnalyzer.analyze(data, p, today = today)
        val l = a.ledger

        assertEquals(2400.0, l.surplusKcal, 0.001)
        assertEquals(-200.0, l.deficitKcal, 0.001)
        assertEquals(4, l.daysAboveTdee)
        assertEquals(1, l.daysBelowTdee)
        assertEquals(2200.0 / UserProfile.KCAL_PER_KG, l.netKg, 0.00001)
        assertTrue(l.netKgComposition > l.netKg)
    }

    @Test
    fun `dan ispod cilja ali iznad odrzavanja i dalje doprinosi dobitku`() {
        val p = profile(GoalMode.BULK)
        val tdee = p.tdee
        val target = p.targetCalories.toDouble()
        // Cilj je iznad TDEE. Dan tik iznad odrzavanja je manjak u odnosu na cilj,
        // ali i dalje goji — to je tacno situacija koju korisnik opisuje.
        val nearMaintenance = tdee + 50
        assertTrue("test pretpostavlja da je dan ispod cilja", nearMaintenance < target - a_tolerance(p))

        val data = days(today.minusDays(2), target, nearMaintenance, target)
        val a = StreakAnalyzer.analyze(data, p, today = today)
        val l = a.ledger

        assertEquals(1, a.missDays)
        // Nijedan promasen dan nije bio ispod odrzavanja...
        assertEquals(0, l.missDaysBelowTdee)
        // ...pa su promaseni dani i dalje doneli pozitivan doprinos.
        assertTrue("ocekivan pozitivan doprinos, bilo ${l.missDaysStillGainedKg}", l.missDaysStillGainedKg > 0)
        // Ali su kostali u odnosu na plan.
        assertTrue("ocekivan negativan trosak, bio ${l.missCostKg}", l.missCostKg < 0)
    }

    @Test
    fun `planirani neto odgovara cilju iznad odrzavanja`() {
        val p = profile(GoalMode.BULK)
        val target = p.targetCalories.toDouble()
        val data = days(today.minusDays(9), *DoubleArray(10) { target })

        val a = StreakAnalyzer.analyze(data, p, today = today)

        // Svi dani na cilju => stvarni neto == planirani neto.
        assertEquals(a.ledger.plannedNetKg, a.ledger.netKg, 0.00001)
        assertEquals(0.0, a.ledger.missCostKg, 0.00001)
    }

    @Test
    fun `stvarna tezina se ne racuna bez dovoljno merenja`() {
        val p = profile(GoalMode.BULK)
        val data = days(today.minusDays(4), *DoubleArray(5) { p.tdee + 500 })

        val a = StreakAnalyzer.analyze(data, p, today = today)

        assertNull(a.ledger.actualWeightChangeKg)
    }

    @Test
    fun `stvarna tezina se racuna kada ima dovoljno merenja`() {
        val history = (0..29).map {
            WeightEntry(date = today.minusDays(29L - it), weightKg = 80.0 + it * 0.05)
        }
        val p = profile(GoalMode.BULK).copy(weightHistory = history)
        val data = days(today.minusDays(29), *DoubleArray(30) { p.tdee + 500 })

        val a = StreakAnalyzer.analyze(data, p, today = today)
        val actual = a.ledger.actualWeightChangeKg

        assertNotNull(actual)
        // Rast 0.05 kg/dan preko ~23 dana izmedju tezista prozora => ~1.15 kg
        assertEquals(1.15, actual!!, 0.15)
        assertTrue(a.ledger.actualSpanDays >= 20)
    }

    @Test
    fun `prazna istorija ne puca`() {
        val a = StreakAnalyzer.analyze(emptyMap(), profile(), today = today)
        assertEquals(0, a.trackedDays)
        assertEquals(0, a.adherencePercent)
    }
}
