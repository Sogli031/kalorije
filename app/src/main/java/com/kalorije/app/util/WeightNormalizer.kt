package com.kalorije.app.util

import java.time.LocalTime
import kotlin.math.exp

/**
 * Pomoćne kalkulacije vezane za težinu.
 *
 * NAPOMENA: raniji model "normalizacije" merenja (korekcija za hranu u stomaku,
 * vodu iz ugljenih hidrata i dehidrataciju tokom dana) je uklonjen. Njegova
 * nesigurnost je bila uporediva sa samom korekcijom, a najveći izvor dnevnog
 * šuma — unos tečnosti — nije se ni pratio. Težina se sada čuva tačno onako
 * kako je izmerena; za tačan trend meri ujutru, na prazan stomak.
 *
 * Ostaje TRT model vodene retencije, koji radi na skali nedelja (ne sati)
 * i koristi se u analizi ciklusa.
 */
object WeightNormalizer {

    // TRT water retention constants (based on PubMed research and deep research analysis)
    // Sigmoidni model: maxRetention * (1 - e^(-k * weeks))
    // Modelira farmakokinetiku testosterona: dugi estri (enantat/cipionat) dostižu
    // steady-state za ~4-6 nedelja, pa voda tu i plato dostiže.
    // maxRetention = BASE + (doza/100)*PER_100MG. Skalira sa dozom uz malu bazu:
    //   120mg → ~1.2kg | 400mg → ~2.9kg | 600mg → ~4.1kg (aromatizujući compound bez AI)
    // NAPOMENA: pretpostavlja aromatizujući compound (test/deca/dbol); tren ~ne zadržava vodu.
    private const val BASE_WATER_RETENTION = 0.5 // kg mala bazna retencija (nezavisna od doze)
    private const val WATER_RETENTION_PER_100MG = 0.6 // kg po 100mg/nedelja (dozno-zavisno)
    private const val SIGMOID_RATE = 0.5 // brzina ramp-up-a: plato ~ned.5-6 (k u eksp. modelu)
    private const val DEFAULT_TRT_DOSE_MG_WEEK = 400.0 // pretpostavljena doza za 300-500mg range
    private const val AI_WATER_REDUCTION_FACTOR = 0.7 // AI smanjuje water retention za ~30%

    /**
     * Procena water retention na osnovu (frakcionih) nedelja na ciklusu, doze i AI.
     *
     * Sigmoidni model: maxRetention * (1 - e^(-k * weeks))
     * - maxRetention = bazna (0.5kg) + dozno-zavisna komponenta (0.6kg/100mg)
     * - k = 0.5 (dugi estri: steady-state i plato vode za ~4-6 nedelja)
     * - AI faktor: -30% ako koristi aromatase inhibitor
     *
     * Kriva daje glatki ramp-up: ~39% ned.1, ~63% ned.2, ~86% ned.4, ~95% ned.6, ~98% ned.8
     * Confidence: ±60% (ned 1-2), ±50% (ned 3-4), ±40% (ned 5+)
     *
     * @param weeksOnCycle Frakcione nedelje na ciklusu (dani/7.0; 0.0 = start ciklusa)
     * @param doseMgPerWeek Nedeljina doza u mg (default 400)
     * @param usingAi Da li koristi aromatase inhibitor
     * @return Procenjeni water retention u kg
     */
    fun estimateWaterRetention(
        weeksOnCycle: Double,
        doseMgPerWeek: Double = DEFAULT_TRT_DOSE_MG_WEEK,
        usingAi: Boolean = false
    ): Double {
        if (weeksOnCycle <= 0.0) return 0.0

        // Maksimalna retencija = bazna + dozno-zavisna komponenta
        val maxRetention = BASE_WATER_RETENTION + (doseMgPerWeek / 100.0) * WATER_RETENTION_PER_100MG

        // Sigmoidni ramp-up: glatka kriva umesto stepenaste
        val retentionFactor = 1.0 - exp(-SIGMOID_RATE * weeksOnCycle)

        // AI adjustment: -30% ako koristi
        val aiMultiplier = if (usingAi) AI_WATER_REDUCTION_FACTOR else 1.0

        return maxRetention * retentionFactor * aiMultiplier
    }

    /**
     * Vraća range water retention-a (lower, estimate, upper) u kg.
     * Confidence interval je širi u ranim nedeljama zbog veće individualne varijacije:
     * - Nedelja 1-2: ±60% (farmakokinetika još nije u steady-state)
     * - Nedelja 3-4: ±50% (prelazna faza)
     * - Nedelja 5+: ±40% (stabilnija procena)
     */
    fun estimateWaterRetentionRange(
        weeksOnCycle: Double,
        doseMgPerWeek: Double = DEFAULT_TRT_DOSE_MG_WEEK,
        usingAi: Boolean = false
    ): Triple<Double, Double, Double> {
        val estimate = estimateWaterRetention(weeksOnCycle, doseMgPerWeek, usingAi)
        val margin = confidenceMarginForWeek(weeksOnCycle.toInt())
        val lower = estimate * (1.0 - margin)
        val upper = estimate * (1.0 + margin)
        return Triple(lower, estimate, upper)
    }

    /**
     * Vraća confidence margin (0.0-1.0) za datu nedelju ciklusa.
     * Širi margin u ranim nedeljama odražava veću neizvesnost.
     */
    fun confidenceMarginForWeek(weeksOnCycle: Int): Double = when {
        weeksOnCycle <= 2 -> 0.60 // ±60% - farmakokinetika još nije u steady-state
        weeksOnCycle <= 4 -> 0.50 // ±50% - prelazna faza
        else -> 0.40               // ±40% - stabilnija procena
    }

    /**
     * Parse time string "HH:mm" to LocalTime with proper validation.
     * Returns null if format is invalid or values out of range.
     */
    fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null

            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null

            // Validate ranges
            if (hour !in 0..23 || minute !in 0..59) return null

            LocalTime.of(hour, minute)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Format LocalTime to "HH:mm" string
     */
    fun formatTime(time: LocalTime): String {
        return String.format("%02d:%02d", time.hour, time.minute)
    }
}
