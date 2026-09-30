package com.kalorije.app.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Represents a completed TRT/AAS cycle with analysis data
 */
data class CycleHistory(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startWeight: Double,
    val endWeight: Double,
    val avgDailyCalories: Int,
    val totalWorkoutDays: Int = 0, // Future: track workouts
    val notes: String = "",
    val doseMgPerWeek: Double = 400.0, // Doza tokom ciklusa
    val usedAi: Boolean = false, // Da li je koristio aromatase inhibitor
    val tdee: Int = 0 // TDEE u momentu završetka ciklusa (0 = stari podatak bez TDEE)
) {
    /**
     * Procenjena water retention na kraju ciklusa (plateau vrednost).
     * Ovo je količina vode koja će otići u naredne 2-4 nedelje.
     */
    val estimatedWaterRetention: Double
        get() = com.kalorije.app.util.WeightNormalizer.estimateWaterRetention(
            weeksOnCycle = durationDays / 7.0, // Frakcione nedelje ciklusa (0.0 = start)
            doseMgPerWeek = doseMgPerWeek,
            usingAi = usedAi
        )

    /**
     * Procenjena "suva" težina na kraju ciklusa (bez vode).
     */
    val estimatedDryEndWeight: Double
        get() = endWeight - estimatedWaterRetention

    /**
     * Procenjeni stvarni dobitak mišićne mase (bez vode).
     */
    val estimatedLeanGain: Double
        get() = estimatedDryEndWeight - startWeight

    /**
     * Timeline gubitka vode posle ciklusa.
     * Vraća listu parova (nedelja, preostala voda u kg).
     */
    fun getWaterLossTimeline(): List<Pair<String, Double>> {
        val peakWater = estimatedWaterRetention
        return listOf(
            "Sada" to peakWater,
            "Nedelja 1-2" to peakWater * 0.4, // Ostaje 30-50%
            "Nedelja 2-4" to peakWater * 0.15, // Ostaje 10-20%
            "Nedelja 4+" to 0.0 // Normalizovano
        )
    }
    /**
     * Duration of the cycle in weeks (zaokruženo, samo za PRIKAZ).
     * Za tempo koristi durationWeeksExact — ChronoUnit.WEEKS seče naniže,
     * pa je ciklus od 20 dana ranije računat kao 2 nedelje i tempo je bio
     * precenjen za ~43%.
     */
    val durationWeeks: Int
        get() = Math.round(durationWeeksExact).toInt().coerceAtLeast(1)

    /**
     * Duration in days
     */
    val durationDays: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(1)

    /**
     * Frakcione nedelje trajanja — koristi se za sve kalkulacije tempa.
     */
    val durationWeeksExact: Double
        get() = (durationDays / 7.0).coerceAtLeast(1.0 / 7.0)

    /**
     * Total weight change during cycle
     */
    val weightChange: Double
        get() = endWeight - startWeight

    /**
     * Average weekly weight change (nekorigovan - uključuje water retention)
     */
    val weeklyWeightChange: Double
        get() = weightChange / durationWeeksExact

    /**
     * Korigovani nedeljni tempo (bez water retention-a).
     * Koristi se za ocenu ciklusa i preporuke.
     */
    val adjustedWeeklyWeightChange: Double
        get() = estimatedLeanGain / durationWeeksExact

    /**
     * Whether weight was gained (true) or lost (false)
     */
    val isGain: Boolean
        get() = weightChange > 0

    /**
     * Da li je ciklus bio bulk (kalorijski surplus) ili cut (deficit).
     * Koristi TDEE ako je sačuvan, inače fallback na korigovani nedeljni tempo.
     */
    val isBulkCycle: Boolean
        get() = if (tdee > 0) avgDailyCalories > tdee else adjustedWeeklyWeightChange > 0.1

    /**
     * Rating of the cycle based on results.
     * Supports both bulking (weight gain) and recomp/cutting cycles.
     */
    val cycleRating: CycleRating
        get() {
            val isBulk = isBulkCycle

            return if (isBulk) {
                // Bulking kriterijumi — SUVI (lean) tempo, bez vode.
                // Realan tempo čistog tkiva je ~0.1-0.35 kg/ned (enhanced), a ne 0.3-0.7
                // (to su brojevi za ukupan/mokri bulk tempo).
                val weeklyChange = adjustedWeeklyWeightChange
                when {
                    // Odličan: čist dobitak 0.15-0.35 kg/ned
                    weeklyChange in 0.15..0.35 -> CycleRating.EXCELLENT
                    // Dobar: solidan čist dobitak
                    weeklyChange in 0.1..0.5 -> CycleRating.GOOD
                    // Solidan: nešto čistog dobitka
                    weeklyChange > 0.05 -> CycleRating.OKAY
                    // Slabo: minimalan dobitak
                    else -> CycleRating.POOR
                }
            } else {
                // Cut/recomp kriterijumi — SIROVI tempo gubitka (≈ tempo gubitka masti).
                // Voda je stabilna tokom cut-a pa se NE oduzima kao kod bulk-a; oduzimanje
                // platoa vode iskrivilo bi tempo u lažno prebrz gubitak.
                val weeklyChange = weeklyWeightChange
                when {
                    // Odličan: umeren gubitak (0.3-0.7 kg/ned)
                    weeklyChange in -0.7..-0.3 -> CycleRating.EXCELLENT
                    // Dobar: solidan gubitak
                    weeklyChange in -0.8..-0.2 -> CycleRating.GOOD
                    // Solidan: bilo kakav gubitak
                    weeklyChange < 0 -> CycleRating.OKAY
                    // Slabo: nema gubitka na cut-u
                    else -> CycleRating.POOR
                }
            }
        }

    /**
     * Generate improvement suggestions based on cycle data.
     * Supports both bulking and cutting cycles.
     */
    fun getImprovementSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        val isBulk = isBulkCycle

        // Weight change suggestions - different for bulk vs cut
        if (isBulk) {
            // Bulk: SUVI (lean) tempo, bez vode
            val weeklyChange = adjustedWeeklyWeightChange
            when {
                weeklyChange < 0 -> suggestions.add("Izgubio si težinu na bulk ciklusu. Povećaj kalorije za 300-500 kcal dnevno.")
                weeklyChange < 0.1 -> suggestions.add("Spor čist napredak (${String.format("%.2f", weeklyChange)} kg/ned suvo). Povećaj kalorije za 200-300 kcal.")
                weeklyChange > 0.5 -> suggestions.add("Brz napredak (${String.format("%.2f", weeklyChange)} kg/ned suvo). Veći deo može biti mast - smanji kalorije za 200 kcal.")
                weeklyChange in 0.15..0.4 -> suggestions.add("Odličan tempo čistog rasta za bulk!")
            }
        } else {
            // Cut: SIROVI tempo gubitka (voda stabilna tokom cut-a, ne oduzima se)
            val weeklyChange = weeklyWeightChange
            when {
                weeklyChange > 0 -> suggestions.add("Dobio si težinu na cut ciklusu. Smanji kalorije za 200-300 kcal dnevno.")
                weeklyChange < -0.8 -> suggestions.add("Prebrz gubitak (${String.format("%.2f", weeklyChange)} kg/ned). Rizik od gubitka mišića - povećaj kalorije malo.")
                weeklyChange in -0.6..-0.3 -> suggestions.add("Odličan tempo za cut! Čuvaš mišiće dok gubiš mast.")
                weeklyChange > -0.2 -> suggestions.add("Spor gubitak (${String.format("%.2f", weeklyChange)} kg/ned). Smanji kalorije za 100-200 kcal ili dodaj kardio.")
            }
        }

        // Duration suggestions
        when {
            durationWeeks < 8 -> suggestions.add("Ciklus je bio kratak ($durationWeeks ned). Optimalno je 12-16 nedelja.")
            durationWeeks > 20 -> suggestions.add("Ciklus je bio dug ($durationWeeks ned). Razmisli o kraćem sledećem (12-16 ned).")
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Odlično odrađen ciklus! Nastavi ovako.")
        }

        return suggestions
    }
}

enum class CycleRating(val displayName: String, val emoji: String) {
    EXCELLENT("Odličan", "🏆"),
    GOOD("Dobar", "👍"),
    OKAY("Solidan", "👌"),
    POOR("Može bolje", "📈")
}

/**
 * Live statistics and recommendations for an active TRT cycle
 */
data class LiveCycleStats(
    val weeksOnCycle: Int,
    val daysOnCycle: Int,
    val startWeight: Double,
    val currentWeight: Double,
    val avgDailyCalories: Int,
    val isWaterRetentionPhase: Boolean,
    val doseMgPerWeek: Double = 400.0, // Default za 300-500mg range
    val usingAi: Boolean = false, // Aromatase inhibitor
    val tdee: Int = 0 // TDEE korisnika
) {
    val weightChange: Double
        get() = currentWeight - startWeight

    /**
     * Frakcione nedelje na ciklusu — `weeksOnCycle` je 1-based prikazni broj
     * (dan 14 → "nedelja 3"), pa bi deljenje njime potcenilo tempo za do celu nedelju.
     */
    val weeksOnCycleExact: Double
        get() = (daysOnCycle / 7.0).coerceAtLeast(1.0 / 7.0)

    val weeklyWeightChange: Double
        get() = weightChange / weeksOnCycleExact

    /**
     * Nedeljni tempo korigovan za vodu (suvi dobitak po nedelji)
     */
    val adjustedWeeklyWeightChange: Double
        get() = estimatedLeanGain / weeksOnCycleExact

    /**
     * Da li je ciklus bulk (kalorijski surplus) ili cut.
     * Koristi TDEE ako je dostupan, inače fallback na nedeljnu promenu težine.
     */
    val isBulkCycle: Boolean
        get() = if (tdee > 0) avgDailyCalories > tdee else adjustedWeeklyWeightChange > 0.1

    /**
     * Procenjena water retention u kg na osnovu nedelje ciklusa, doze i AI.
     * Koristi evidence-based formulu iz WeightNormalizer.
     */
    val estimatedWaterRetention: Double
        get() = com.kalorije.app.util.WeightNormalizer.estimateWaterRetention(
            weeksOnCycle = daysOnCycle / 7.0, // Frakcione nedelje (0.0 = start ciklusa)
            doseMgPerWeek = doseMgPerWeek,
            usingAi = usingAi
        )

    /**
     * Procenjena "prava" težina bez water retention-a
     */
    val estimatedDryWeight: Double
        get() = currentWeight - estimatedWaterRetention

    /**
     * Promena težine korigovana za water retention (aproksimacija mišićne mase)
     */
    val estimatedLeanGain: Double
        get() = estimatedDryWeight - startWeight

    /**
     * Generate live recommendations that user can act on NOW
     */
    fun getLiveRecommendations(): List<LiveRecommendation> {
        val recommendations = mutableListOf<LiveRecommendation>()

        // Water retention info - detaljno po nedelji prema researchu
        val maxWaterRetention = com.kalorije.app.util.WeightNormalizer.estimateWaterRetention(
            weeksOnCycle = 8.0, // Plateau
            doseMgPerWeek = doseMgPerWeek,
            usingAi = usingAi
        )
        val remainingWater = (maxWaterRetention - estimatedWaterRetention).coerceAtLeast(0.0)

        // Poruka specifična za fazu ciklusa
        val phaseMessage = when {
            weeksOnCycle <= 2 -> "Brza akumulacija vode (40-65% od max). Očekuj još ~${String.format("%.1f", remainingWater)} kg."
            weeksOnCycle <= 4 -> "Nastavak akumulacije (75-85% od max). Očekuj još ~${String.format("%.1f", remainingWater)} kg."
            weeksOnCycle <= 6 -> "Blizu stabilizacije (90-95% od max). Preostalo ~${String.format("%.1f", remainingWater)} kg."
            else -> "Stabilna faza - voda dostigla plato."
        }

        val phaseTitle = when {
            weeksOnCycle <= 2 -> "Nedelja $weeksOnCycle: Rana faza"
            weeksOnCycle <= 4 -> "Nedelja $weeksOnCycle: Srednja faza"
            weeksOnCycle <= 6 -> "Nedelja $weeksOnCycle: Kasna faza"
            else -> "Nedelja $weeksOnCycle: Plato"
        }

        if (isWaterRetentionPhase) {
            recommendations.add(
                LiveRecommendation(
                    emoji = "💧",
                    title = "$phaseTitle (~${String.format("%.1f", estimatedWaterRetention)} kg vode)",
                    message = phaseMessage,
                    type = RecommendationType.INFO
                )
            )
        } else {
            // Stabilna faza - pokazuj procenjenu "suvu" težinu
            recommendations.add(
                LiveRecommendation(
                    emoji = "💧",
                    title = "$phaseTitle (~${String.format("%.1f", estimatedWaterRetention)} kg vode)",
                    message = "Procenjena suva težina: ${String.format("%.1f", estimatedDryWeight)} kg (${if (estimatedLeanGain >= 0) "+" else ""}${String.format("%.1f", estimatedLeanGain)} kg mišića)",
                    type = RecommendationType.INFO
                )
            )
        }

        // Podsetnik ako nema unosa hrane
        if (avgDailyCalories == 0) {
            recommendations.add(
                LiveRecommendation(
                    emoji = "🍽️",
                    title = "Unesi hranu",
                    message = "Nema podataka o ishrani. Unesi obroke da dobiješ preporuke.",
                    type = RecommendationType.WARNING
                )
            )
        }

        // Weight rate recommendations (only after water retention phase)
        if (!isWaterRetentionPhase && weeksOnCycle >= 4) {
            if (isBulkCycle) {
                // Bulk: SUVI (lean) tempo, korigovan za vodu
                val adjustedWeeklyChange = adjustedWeeklyWeightChange
                when {
                    adjustedWeeklyChange < 0 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "📉",
                                title = "Gubiš težinu!",
                                message = "Povećaj kalorije za 400-500 kcal. Dodaj obrok pre spavanja.",
                                type = RecommendationType.CRITICAL
                            )
                        )
                    }
                    adjustedWeeklyChange < 0.1 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "🐢",
                                title = "Spor napredak",
                                message = "Samo ${String.format("%.2f", adjustedWeeklyChange)} kg/ned suvo. Dodaj 200-300 kcal dnevno.",
                                type = RecommendationType.WARNING
                            )
                        )
                    }
                    adjustedWeeklyChange > 0.5 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "🏃",
                                title = "Prebrz napredak",
                                message = "${String.format("%.2f", adjustedWeeklyChange)} kg/ned suvo može biti mast. Smanji za 200 kcal.",
                                type = RecommendationType.WARNING
                            )
                        )
                    }
                    adjustedWeeklyChange in 0.15..0.4 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "🎯",
                                title = "Idealan tempo!",
                                message = "${String.format("%.2f", adjustedWeeklyChange)} kg/ned suvo je savršeno za kvalitetan rast.",
                                type = RecommendationType.SUCCESS
                            )
                        )
                    }
                }
            } else {
                // Cut: SIROVI tempo gubitka (voda stabilna tokom cut-a, ne oduzima se)
                val weeklyLoss = weeklyWeightChange
                when {
                    weeklyLoss > 0 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "📈",
                                title = "Dobijaš na cut-u",
                                message = "Trebalo bi da gubiš. Smanji kalorije za 200-300 kcal dnevno.",
                                type = RecommendationType.WARNING
                            )
                        )
                    }
                    weeklyLoss < -0.8 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "⚠️",
                                title = "Prebrz gubitak",
                                message = "${String.format("%.2f", weeklyLoss)} kg/ned — rizik za mišiće. Povećaj kalorije malo.",
                                type = RecommendationType.WARNING
                            )
                        )
                    }
                    weeklyLoss in -0.7..-0.3 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "🎯",
                                title = "Idealan tempo cut-a",
                                message = "${String.format("%.2f", weeklyLoss)} kg/ned — čuvaš mišiće dok gubiš mast.",
                                type = RecommendationType.SUCCESS
                            )
                        )
                    }
                    weeklyLoss > -0.2 -> {
                        recommendations.add(
                            LiveRecommendation(
                                emoji = "🐢",
                                title = "Spor gubitak",
                                message = "Samo ${String.format("%.2f", weeklyLoss)} kg/ned. Smanji 100-200 kcal ili dodaj kardio.",
                                type = RecommendationType.WARNING
                            )
                        )
                    }
                }
            }
        }

        // Cycle duration reminder
        when {
            weeksOnCycle == 8 -> {
                recommendations.add(
                    LiveRecommendation(
                        emoji = "📅",
                        title = "8 nedelja",
                        message = "Polovica standardnog ciklusa. Razmisli o ciljevima za druge polovine.",
                        type = RecommendationType.INFO
                    )
                )
            }
            weeksOnCycle == 12 -> {
                recommendations.add(
                    LiveRecommendation(
                        emoji = "📅",
                        title = "12 nedelja",
                        message = "Standardna dužina ciklusa. Možeš završiti ili nastaviti do 16 ned.",
                        type = RecommendationType.INFO
                    )
                )
            }
            weeksOnCycle >= 16 -> {
                recommendations.add(
                    LiveRecommendation(
                        emoji = "⏰",
                        title = "Dug ciklus",
                        message = "$weeksOnCycle nedelja. Razmisli o završetku i PCT-u.",
                        type = RecommendationType.WARNING
                    )
                )
            }
        }

        return recommendations
    }
}

data class LiveRecommendation(
    val emoji: String,
    val title: String,
    val message: String,
    val type: RecommendationType
)

enum class RecommendationType {
    SUCCESS,
    INFO,
    WARNING,
    CRITICAL
}
