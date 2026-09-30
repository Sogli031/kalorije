package com.kalorije.app.util

import com.kalorije.app.data.model.WeeklyWeight
import com.kalorije.app.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyWeightCalculatorTest {

    // Prvo merenje u sredu 16.09.2026 — periodi kreću od tog dana, ne od ponedeljka
    private val day1 = LocalDate.of(2026, 9, 16)

    private fun entries(start: LocalDate, vararg weights: Double?): List<WeightEntry> =
        weights.mapIndexedNotNull { i, w ->
            w?.let { WeightEntry(date = start.plusDays(i.toLong()), weightKg = it, time = "07:00") }
        }

    @Test
    fun `period krece od dana prvog merenja, ne od ponedeljka`() {
        assertEquals(DayOfWeek.WEDNESDAY, day1.dayOfWeek)
        for (i in 0L..6L) {
            assertEquals(day1, WeeklyWeightCalculator.weekStartOf(day1.plusDays(i), anchor = day1))
        }
        assertEquals(day1.plusDays(7), WeeklyWeightCalculator.weekStartOf(day1.plusDays(7), anchor = day1))
        assertEquals(day1.plusDays(14), WeeklyWeightCalculator.weekStartOf(day1.plusDays(20), anchor = day1))
    }

    @Test
    fun `datumi pre anchora padaju u ispravan raniji period`() {
        assertEquals(day1.minusDays(7), WeeklyWeightCalculator.weekStartOf(day1.minusDays(1), anchor = day1))
        assertEquals(day1.minusDays(7), WeeklyWeightCalculator.weekStartOf(day1.minusDays(7), anchor = day1))
        assertEquals(day1.minusDays(14), WeeklyWeightCalculator.weekStartOf(day1.minusDays(8), anchor = day1))
    }

    @Test
    fun `bez anchora uzima se najstarije merenje`() {
        val history = entries(day1, 80.0, 80.4, 79.8, 80.2)
        val w = WeeklyWeightCalculator.compute(history, anchor = null, today = day1.plusDays(7)).single()
        assertEquals(day1, w.weekStart)
    }

    @Test
    fun `puna nedelja daje prosek svih 7 dana`() {
        val history = entries(day1, 80.0, 80.4, 79.8, 80.2, 80.6, 80.1, 79.9)
        val weeks = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(7))

        assertEquals(1, weeks.size)
        val w = weeks.single()
        assertEquals(day1, w.weekStart)
        assertEquals(day1.plusDays(6), w.weekEnd)
        assertEquals(7, w.daysMeasured)
        assertEquals(80.142857, w.averageKg, 0.0001)
        assertTrue(w.isValid)
        assertTrue(w.isComplete)
    }

    @Test
    fun `propusten dan-dva ne smeta - nedelja i dalje vazi`() {
        // Fale 3. i 6. dan → 5 merenja
        val history = entries(day1, 80.0, 80.4, null, 80.2, 80.6, null, 79.9)
        val w = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(7)).single()

        assertEquals(5, w.daysMeasured)
        assertTrue(w.isValid)
        assertEquals((80.0 + 80.4 + 80.2 + 80.6 + 79.9) / 5, w.averageKg, 0.0001)
    }

    @Test
    fun `tacno 4 merenja je donja granica - vazi`() {
        val history = entries(day1, 80.0, null, 80.4, null, 80.2, null, 80.6)
        val w = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(7)).single()

        assertEquals(WeeklyWeight.MIN_MEASURED_DAYS, w.daysMeasured)
        assertTrue(w.isValid)
    }

    @Test
    fun `ako fale 4 dana (3 merenja) nedelja ne vazi`() {
        val history = entries(day1, 80.0, null, null, 80.4, null, null, 80.2)
        val w = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(7)).single()

        assertEquals(3, w.daysMeasured)
        assertFalse(w.isValid)
        assertTrue(WeeklyWeightCalculator.validWeeks(history, anchor = day1, today = day1.plusDays(7)).isEmpty())
    }

    @Test
    fun `nedelja u toku nije kompletna ali moze da vazi cim ima 4 merenja`() {
        val history = entries(day1, 80.0, 80.4, 80.2, 80.6) // dan 1–4
        val day4 = day1.plusDays(3)
        val w = WeeklyWeightCalculator.compute(history, anchor = day1, today = day4).single()

        assertFalse(w.isComplete)
        assertTrue(w.isValid)
        assertEquals(4, w.daysMeasured)
    }

    @Test
    fun `nedelja postaje kompletna tek 8 dana od prvog merenja`() {
        val history = entries(day1, 80.0, 80.4, 80.2, 80.6, 80.1)
        val day7 = day1.plusDays(6)
        val day8 = day1.plusDays(7)

        assertFalse(WeeklyWeightCalculator.compute(history, anchor = day1, today = day7).single().isComplete)
        assertTrue(WeeklyWeightCalculator.compute(history, anchor = day1, today = day8).single().isComplete)
    }

    @Test
    fun `sedmi i osmi dan idu u razlicite periode`() {
        val history = entries(day1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 82.0)
        val weeks = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(14))

        assertEquals(2, weeks.size)
        assertEquals(7, weeks[0].daysMeasured)
        assertEquals(80.0, weeks[0].averageKg, 0.0001)
        assertEquals(day1.plusDays(7), weeks[1].weekStart)
        assertEquals(1, weeks[1].daysMeasured)
        assertEquals(82.0, weeks[1].averageKg, 0.0001)
    }

    @Test
    fun `anchor ostaje isti i kad se prvo merenje obrise`() {
        // Obrisan dan 1 — sa sačuvanim anchorom period i dalje kreće od day1
        val history = entries(day1, null, 80.2, 80.4, 80.6, 80.8, 81.0, 81.2, 90.0)
        val weeks = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(14))

        assertEquals(2, weeks.size)
        assertEquals(day1, weeks[0].weekStart)
        assertEquals(6, weeks[0].daysMeasured)
        assertEquals(day1.plusDays(7), weeks[1].weekStart)
    }

    @Test
    fun `vise nedelja se sortira od najstarije, nedelje bez merenja se preskacu`() {
        val week1 = entries(day1, 80.0, 80.2, 80.4, 80.6, 80.8)
        // nedelja 2 potpuno preskočena
        val week3 = entries(day1.plusDays(14), 81.0, 81.2, 81.4, 81.6)
        val history = (week3 + week1).shuffled() // redosled u istoriji ne sme da utiče

        val weeks = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(21))

        assertEquals(2, weeks.size)
        assertEquals(day1, weeks[0].weekStart)
        assertEquals(day1.plusDays(14), weeks[1].weekStart)
        assertEquals(80.4, weeks[0].averageKg, 0.0001)
        assertEquals(81.3, weeks[1].averageKg, 0.0001)
    }

    @Test
    fun `svaki dan ucestvuje u proseku samo jednom`() {
        // Dva unosa za isti dan (ne bi trebalo da se desi, ali ne sme da razbije prosek)
        val history = entries(day1, 80.0, 80.0, 80.0, 80.0) +
            listOf(WeightEntry(date = day1, weightKg = 90.0, time = "20:00"))

        val w = WeeklyWeightCalculator.compute(history, anchor = day1, today = day1.plusDays(7)).single()

        assertEquals(4, w.daysMeasured)
        // poslednji unos za taj dan pobeđuje → (90 + 80 + 80 + 80) / 4
        assertEquals(82.5, w.averageKg, 0.0001)
    }

    @Test
    fun `prazna istorija daje praznu listu`() {
        assertTrue(WeeklyWeightCalculator.compute(emptyList(), anchor = day1, today = day1).isEmpty())
    }
}
