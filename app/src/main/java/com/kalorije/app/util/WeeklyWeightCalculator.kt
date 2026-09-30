package com.kalorije.app.util

import com.kalorije.app.data.model.WeeklyWeight
import com.kalorije.app.data.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Grupisanje dnevnih merenja u 7-dnevne periode ("nedelje") i računanje proseka.
 *
 * Periodi NE prate kalendar (pon–ned) nego kreću od dana prvog merenja (`anchor`):
 * dani 1–7 su prvi period, 8–14 drugi itd. Tako prva nedelja uvek ima punih 7 dana
 * bez obzira na to kog dana u nedelji je korisnik počeo.
 *
 * Čisto izvedeni podaci — ništa se ne čuva posebno, sve se računa iz `weightHistory`,
 * pa brisanje/izmena dnevnog merenja automatski ažurira period.
 */
object WeeklyWeightCalculator {

    const val PERIOD_DAYS = 7L

    /** Prvi dan 7-dnevnog perioda kome datum pripada, računato od `anchor`. */
    fun weekStartOf(date: LocalDate, anchor: LocalDate): LocalDate {
        val daysFromAnchor = ChronoUnit.DAYS.between(anchor, date)
        // floorDiv da bi i datumi PRE anchora (stara istorija) pali u ispravan period
        val periodIndex = Math.floorDiv(daysFromAnchor, PERIOD_DAYS)
        return anchor.plusDays(periodIndex * PERIOD_DAYS)
    }

    /**
     * Svi periodi koji imaju bar jedno merenje, sortirani od najstarijeg ka najnovijem.
     * Periodi bez ijednog merenja se ne pojavljuju.
     *
     * @param anchor dan 1 prvog perioda (datum prvog merenja). Ako je null, uzima se
     *               najstarije merenje iz istorije.
     *
     * Ako (nekim čudom) postoji više merenja za isti dan, uzima se poslednje u listi —
     * svaki dan učestvuje u proseku tačno jednom, da dan sa više merenja ne bi vukao prosek.
     */
    fun compute(
        history: List<WeightEntry>,
        anchor: LocalDate? = null,
        today: LocalDate = getLogicalDate()
    ): List<WeeklyWeight> {
        if (history.isEmpty()) return emptyList()
        val periodAnchor = anchor ?: history.minOf { it.date }

        val weightByDay: Map<LocalDate, Double> = history.associate { it.date to it.weightKg }

        return weightByDay.entries
            .groupBy { weekStartOf(it.key, periodAnchor) }
            .map { (weekStart, days) ->
                WeeklyWeight(
                    weekStart = weekStart,
                    averageKg = days.map { it.value }.average(),
                    daysMeasured = days.size,
                    isComplete = today.isAfter(weekStart.plusDays(PERIOD_DAYS - 1))
                )
            }
            .sortedBy { it.weekStart }
    }

    /** Samo periodi sa dovoljno merenja (≥ [WeeklyWeight.MIN_MEASURED_DAYS]), od najstarijeg ka najnovijem. */
    fun validWeeks(
        history: List<WeightEntry>,
        anchor: LocalDate? = null,
        today: LocalDate = getLogicalDate()
    ): List<WeeklyWeight> = compute(history, anchor, today).filter { it.isValid }
}
