package com.kalorije.app.util

import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.data.model.UserProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kako je dan ocenjen u odnosu na kalorijski cilj.
 */
enum class DayStatus {
    /** Unos u okviru tolerancije oko cilja. */
    ON_TARGET,

    /** Pojedeno osetno više od cilja. */
    OVER,

    /** Pojedeno osetno manje od cilja. */
    UNDER,

    /** Nema nijednog unosa za taj dan — ne zna se šta je pojedeno. */
    NOT_LOGGED
}

/**
 * Da li dan RADI ili PROTIV cilja — za razliku od [DayStatus], ovo zavisi od [GoalMode].
 *
 * Poenta: kad se goji, dan iznad cilja nije promašaj. Kalorije su i dalje išle u
 * pravom smeru, samo brže od plana. Promašaj je samo odstupanje koje te udaljava
 * od cilja: manjak pri dobijanju, višak pri skidanju. Kod održavanja oba smera
 * odmiču od nule pa oba promašuju.
 */
enum class DayOutcome {
    /** Dan radi za cilj (u toleranciji ili u korisnom smeru). */
    HIT,

    /** Dan radi protiv cilja. */
    MISS,

    NOT_LOGGED;

    companion object {
        fun from(status: DayStatus, goalMode: GoalMode): DayOutcome = when (status) {
            DayStatus.NOT_LOGGED -> NOT_LOGGED
            DayStatus.ON_TARGET -> HIT
            // Višak pri dobijanju i manjak pri skidanju guraju ka cilju — nisu promašaj.
            DayStatus.OVER -> if (goalMode == GoalMode.BULK) HIT else MISS
            DayStatus.UNDER -> if (goalMode == GoalMode.CUT) HIT else MISS
        }
    }
}

data class DayResult(
    val date: LocalDate,
    val calories: Double,
    val target: Int,
    val status: DayStatus,
    /** TDEE korišćen za taj dan (potrošnja na održavanju). */
    val tdee: Double = 0.0,
    /** Energetska gustina tkiva korišćena za procenu (kcal/kg). */
    val tissueKcalPerKg: Double = UserProfile.KCAL_PER_KG,
    /** Da li dan radi za cilj — zavisi od [GoalMode], v. [DayOutcome]. */
    val outcome: DayOutcome = DayOutcome.from(status, GoalMode.MAINTENANCE)
) {
    /** Odstupanje u kcal (+ = višak, - = manjak). Za neupisan dan je 0. */
    val deviation: Double
        get() = if (status == DayStatus.NOT_LOGGED) 0.0 else calories - target

    /**
     * Energetski bilans dana u odnosu na ODRŽAVANJE (TDEE), ne u odnosu na cilj.
     * To je veličina koja fizički pomera težinu. Za neupisan dan je 0.
     */
    val energyBalance: Double
        get() = if (status == DayStatus.NOT_LOGGED) 0.0 else calories - tdee

    /**
     * Teorijska promena telesne mase koju taj dnevni bilans nosi, u kg.
     * Konzervativna procena po konvenciji masnog tkiva (7700 kcal/kg).
     */
    val estimatedKgChange: Double
        get() = energyBalance / UserProfile.KCAL_PER_KG

    /**
     * Promena uz uračunatu kompoziciju tkiva (Forbes particija + Hall energetske
     * gustine). Uvek je po apsolutnoj vrednosti >= [estimatedKgChange], jer je nemasno
     * tkivo energetski jeftinije od masti.
     */
    val estimatedKgChangeComposition: Double
        get() = energyBalance / tissueKcalPerKg
}

data class StreakSpan(
    val start: LocalDate,
    val end: LocalDate,
    val outcome: DayOutcome,
    val totalDeviation: Double
) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /**
     * Smer promašaja — period može da meša viškove i manjkove (npr. kod održavanja),
     * pa se smer čita iz zbira odstupanja.
     */
    val direction: DayStatus
        get() = when {
            outcome == DayOutcome.NOT_LOGGED -> DayStatus.NOT_LOGGED
            outcome == DayOutcome.HIT -> DayStatus.ON_TARGET
            totalDeviation >= 0 -> DayStatus.OVER
            else -> DayStatus.UNDER
        }
}

/**
 * Zbir energetskog bilansa u odnosu na ODRŽAVANJE (TDEE) — koliko bi po unosu
 * trebalo da si u plusu ili minusu, i koliko su promašeni dani od toga odneli.
 */
data class TdeeLedger(
    /** Zbir svih kalorija iznad održavanja. */
    val surplusKcal: Double = 0.0,
    /** Zbir svih kalorija ispod održavanja (negativan broj). */
    val deficitKcal: Double = 0.0,
    val daysAboveTdee: Int = 0,
    val daysBelowTdee: Int = 0,
    /** Konzervativna procena neto promene (7700 kcal/kg). */
    val netKg: Double = 0.0,
    /** Ista promena uz uračunatu kompoziciju tkiva — gornja granica. */
    val netKgComposition: Double = 0.0,
    /** Koliko bi bilo da je SVAKI upisani dan pao tačno na cilj. */
    val plannedNetKg: Double = 0.0,
    /**
     * Koliko su promašeni dani odneli u odnosu na cilj, u kg.
     * Negativno = propušten dobitak (manjak u bulku), pozitivno = višak preko plana.
     */
    val missCostKg: Double = 0.0,
    /**
     * Koliko su ti isti promašeni dani IPAK doneli, jer su bili iznad održavanja.
     * Ovo je poenta: dan ispod cilja ali iznad TDEE-a i dalje goji, samo sporije.
     */
    val missDaysStillGainedKg: Double = 0.0,
    /** Od promašenih dana, koliko ih je zaista bilo ispod održavanja. */
    val missDaysBelowTdee: Int = 0,
    /** Stvarna promena na vagi u istom periodu, ako ima dovoljno merenja. */
    val actualWeightChangeKg: Double? = null,
    /** Razmak između merenja korišćenih za [actualWeightChangeKg], u danima. */
    val actualSpanDays: Int = 0
)

/**
 * Rezultat analize doslednosti u odnosu na kalorijski cilj.
 */
data class StreakAnalysis(
    /** Dani od najstarijeg upisanog dana do danas, hronološki. */
    val days: List<DayResult> = emptyList(),
    /** Trenutni niz uzastopnih dana u cilju (računa se unazad od danas/juče). */
    val currentStreak: Int = 0,
    /** Najduži niz dana u cilju u celoj istoriji. */
    val bestStreak: Int = 0,
    val onTargetDays: Int = 0,
    val overDays: Int = 0,
    val underDays: Int = 0,
    val notLoggedDays: Int = 0,
    /** Dani koji rade za cilj (uključuje koristan smer, ne samo tačno pogađanje). */
    val hitDays: Int = 0,
    /** Dani koji rade protiv cilja. */
    val missDays: Int = 0,
    /** Ukupno kcal odstupanja preko svih upisanih dana (+ višak / - manjak). */
    val totalDeviationKcal: Double = 0.0,
    /** Odstupanje pretvoreno u kg (KCAL_PER_KG). + = brže od plana. */
    val deviationKg: Double = 0.0,
    /**
     * Koliko dana napretka je izgubljeno (+) ili dobijeno (-) u odnosu na plan.
     * null kada je cilj održavanje (nema planiranog tempa, pa nema kašnjenja).
     */
    val delayDays: Double? = null,
    /** Planirana promena težine za posmatrani period, u kg. */
    val plannedChangeKg: Double = 0.0,
    /** Stvarno očekivana promena na osnovu unosa, u kg. */
    val expectedChangeKg: Double = 0.0,
    /** Najgori periodi (uzastopni promašeni dani), sortirano po uticaju. */
    val worstSpans: List<StreakSpan> = emptyList(),
    /** Svi nizovi (u cilju i promašeni) hronološki — za traku na vrhu ekrana. */
    val spans: List<StreakSpan> = emptyList(),
    val goalMode: GoalMode = GoalMode.MAINTENANCE,
    val target: Int = 0,
    val toleranceKcal: Int = 0,
    /** TDEE korišćen kao nulta linija za dnevne procene. */
    val tdee: Double = 0.0,
    /** Energetska gustina tkiva iz Forbes particije (kcal/kg). */
    val tissueKcalPerKg: Double = UserProfile.KCAL_PER_KG,
    /** Udeo nemasne mase u promeni, (min, max) kao procenti. */
    val leanPercentRange: Pair<Double, Double> = 0.0 to 0.0,
    /** Zbir prema održavanju — v. [TdeeLedger]. */
    val ledger: TdeeLedger = TdeeLedger()
) {
    val trackedDays: Int get() = days.size
    val loggedDays: Int get() = trackedDays - notLoggedDays

    /** Procenat upisanih dana koji rade za cilj. */
    val adherencePercent: Int
        get() = if (loggedDays == 0) 0 else (hitDays * 100.0 / loggedDays).roundToInt()

    /** Procenat upisanih dana koji su pali tačno u toleranciju oko cilja. */
    val exactTargetPercent: Int
        get() = if (loggedDays == 0) 0 else (onTargetDays * 100.0 / loggedDays).roundToInt()

    /** Da li smer odstupanja uopšte može da bude koristan (kod održavanja ne može). */
    val hasHelpfulDirection: Boolean
        get() = goalMode != GoalMode.MAINTENANCE

    /** Procenat dana koji su uopšte upisani — meri koliko je analiza pouzdana. */
    val loggingCoveragePercent: Int
        get() = if (trackedDays == 0) 0 else (loggedDays * 100.0 / trackedDays).roundToInt()

    /** Zbir dnevnih teorijskih promena (konvencija 7700 kcal/kg), u kg. */
    val estimatedKgFromIntake: Double
        get() = days.sumOf { it.estimatedKgChange }

    /** Isto, ali uz uračunatu kompoziciju tkiva — gornja granica. */
    val estimatedKgFromIntakeComposition: Double
        get() = days.sumOf { it.estimatedKgChangeComposition }

    /** Prosečan dnevni energetski bilans preko upisanih dana (kcal). */
    val avgDailyBalance: Double
        get() = if (loggedDays == 0) 0.0 else days.sumOf { it.energyBalance } / loggedDays

    /** Dan sa najvećim viškom u odnosu na održavanje. */
    val biggestSurplusDay: DayResult?
        get() = days.filter { it.status != DayStatus.NOT_LOGGED }.maxByOrNull { it.energyBalance }

    /** Dan sa najvećim manjkom u odnosu na održavanje. */
    val biggestDeficitDay: DayResult?
        get() = days.filter { it.status != DayStatus.NOT_LOGGED }.minByOrNull { it.energyBalance }
}

/**
 * Računa doslednost u odnosu na dnevni kalorijski cilj i procenjuje koliko su
 * promašeni dani ubrzali ili usporili napredak.
 *
 * Ograničenja koja treba imati u vidu pri čitanju rezultata:
 * - Koristi se TRENUTNI cilj za sve dane u istoriji; app ne čuva istoriju ciljeva,
 *   pa ako je cilj menjan, stari dani su ocenjeni po novom merilu.
 * - Dan se ocenjuje u odnosu na SMER cilja: pri dobijanju višak nije promašaj, pri
 *   skidanju manjak nije promašaj (v. [DayOutcome]). Promašaj je samo odstupanje koje
 *   te udaljava od cilja.
 * - Neupisani dani se NE tretiraju kao promašaj niti ulaze u zbir odstupanja —
 *   za njih se prosto ne zna šta je pojedeno. Zato se prikazuje pokrivenost.
 * - Pretvaranje kcal u kg koristi 7700 kcal/kg (masno tkivo), isto kao ostatak
 *   app-a. Za čist mišić je cena niža, pa je ovo procena reda veličine.
 */
object StreakAnalyzer {

    /** Tolerancija oko cilja: 10% cilja, ali nikad manje od 100 kcal. */
    const val TOLERANCE_PERCENT = 0.10
    const val MIN_TOLERANCE_KCAL = 100

    /**
     * Energetska gustina masnog tkiva (kcal/kg) — Hall 2008, ~39.5 MJ/kg.
     * Veća je od konvencionalnih 7700 jer 7700 je praktična aproksimacija za
     * mešovito tkivo, dok je ovo cena ČISTE masti.
     */
    const val FAT_TISSUE_KCAL_PER_KG = 9440.0

    /**
     * Energetska gustina nemasne mase (kcal/kg) — Hall 2008, ~7.6 MJ/kg.
     * Niska jer je FFM većim delom voda, koja ne nosi energiju.
     */
    const val LEAN_TISSUE_KCAL_PER_KG = 1816.0

    fun toleranceFor(target: Int): Int =
        maxOf(MIN_TOLERANCE_KCAL, (target * TOLERANCE_PERCENT).roundToInt())

    /**
     * Energetska cena 1 kg promene telesne mase za DATOG korisnika.
     *
     * Kombinuje Forbes particiju (koliko promene ide u nemasnu masu, zavisi od
     * telesne masti, treninga i TRT-a — v. [PartitionCalculator.leanFractionRange])
     * sa Hall-ovim energetskim gustinama tkiva:
     *
     *     kcal/kg = p * 1816 + (1 - p) * 9440
     *
     * Za mršavu, treniranu osobu na TRT-u p je visoko pa je cena kilograma niska —
     * isti višak kalorija tu pomera vagu više nego kod netrenirane osobe s više masti.
     *
     * Vraća (kcal_po_kg, leanPercentMin, leanPercentMax).
     */
    fun tissueEnergyDensity(profile: UserProfile, dailyBalanceKcal: Int): Triple<Double, Double, Double> {
        val (leanMin, leanMax) = PartitionCalculator.leanFractionRange(
            fatMassKg = profile.estimatedFatMassKg,
            resistanceTrainingDays = profile.resistanceTrainingDays,
            isOnTrt = profile.useTrt,
            surplusKcalPerDay = dailyBalanceKcal
        )
        val leanMid = (leanMin + leanMax) / 2.0
        val density = leanMid * LEAN_TISSUE_KCAL_PER_KG + (1 - leanMid) * FAT_TISSUE_KCAL_PER_KG
        return Triple(density, leanMin * 100, leanMax * 100)
    }

    fun analyze(
        caloriesByDate: Map<LocalDate, Double>,
        profile: UserProfile,
        target: Int = profile.targetCalories,
        today: LocalDate = getLogicalDate()
    ): StreakAnalysis {
        if (caloriesByDate.isEmpty()) return StreakAnalysis(goalMode = profile.goalMode, target = target)

        val tolerance = toleranceFor(target)
        val tdee = profile.tdee
        val (tissueDensity, leanMin, leanMax) = tissueEnergyDensity(profile, target - tdee.roundToInt())
        val firstDay = caloriesByDate.keys.min()
        val lastDay = minOf(caloriesByDate.keys.max(), today)
        if (lastDay.isBefore(firstDay)) {
            return StreakAnalysis(goalMode = profile.goalMode, target = target, toleranceKcal = tolerance)
        }

        // Popuni SVE dane u rasponu, uključujući one bez ijednog unosa.
        val days = generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .map { date ->
                val cals = caloriesByDate[date]
                val status = when {
                    cals == null -> DayStatus.NOT_LOGGED
                    cals > target + tolerance -> DayStatus.OVER
                    cals < target - tolerance -> DayStatus.UNDER
                    else -> DayStatus.ON_TARGET
                }
                DayResult(
                    date = date,
                    calories = cals ?: 0.0,
                    target = target,
                    status = status,
                    tdee = tdee,
                    tissueKcalPerKg = tissueDensity,
                    outcome = DayOutcome.from(status, profile.goalMode)
                )
            }
            .toList()

        val onTarget = days.count { it.status == DayStatus.ON_TARGET }
        val over = days.count { it.status == DayStatus.OVER }
        val under = days.count { it.status == DayStatus.UNDER }
        val notLogged = days.count { it.status == DayStatus.NOT_LOGGED }
        val hits = days.count { it.outcome == DayOutcome.HIT }
        val misses = days.count { it.outcome == DayOutcome.MISS }

        val totalDeviation = days.sumOf { it.deviation }
        val deviationKg = totalDeviation / UserProfile.KCAL_PER_KG

        // Planirani vs očekivani napredak za period upisanih dana.
        val loggedDays = days.size - notLogged
        val plannedDailyChangeKg = profile.dailySurplus / UserProfile.KCAL_PER_KG
        val plannedChangeKg = plannedDailyChangeKg * loggedDays
        val expectedChangeKg = plannedChangeKg + deviationKg

        // Kašnjenje: koliko dana planiranog tempa vredi nastalo odstupanje.
        // Znak je normalizovan tako da je + uvek "usporenje cilja".
        val delayDays = if (profile.goalMode == GoalMode.MAINTENANCE || profile.dailySurplus == 0) {
            null
        } else {
            val daysWorth = deviationKg / plannedDailyChangeKg // + = napred, - = nazad
            -daysWorth
        }

        val spans = buildSpans(days)
        val worst = spans
            .filter { it.outcome == DayOutcome.MISS }
            .sortedByDescending { abs(it.totalDeviation) }
            .take(5)

        val bestStreak = spans.filter { it.outcome == DayOutcome.HIT }.maxOfOrNull { it.days } ?: 0

        val ledger = buildLedger(days, profile, target, tdee, tissueDensity)

        return StreakAnalysis(
            days = days,
            currentStreak = currentStreak(days, today),
            bestStreak = bestStreak,
            onTargetDays = onTarget,
            overDays = over,
            underDays = under,
            notLoggedDays = notLogged,
            hitDays = hits,
            missDays = misses,
            totalDeviationKcal = totalDeviation,
            deviationKg = deviationKg,
            delayDays = delayDays,
            plannedChangeKg = plannedChangeKg,
            expectedChangeKg = expectedChangeKg,
            worstSpans = worst,
            spans = spans,
            goalMode = profile.goalMode,
            target = target,
            toleranceKcal = tolerance,
            tdee = tdee,
            tissueKcalPerKg = tissueDensity,
            leanPercentRange = leanMin to leanMax,
            ledger = ledger
        )
    }

    /**
     * Sabira sve dane prema ODRŽAVANJU i razdvaja koliko su promašeni dani zaista koštali.
     *
     * Ključna razlika koju ovo pravi: dan ispod cilja nije isto što i dan ispod
     * održavanja. Ako si pojeo manje od cilja ali i dalje iznad TDEE-a, taj dan te
     * i dalje goji — samo sporije nego što je planirano. „Šteta" je razlika u odnosu
     * na cilj, a ne ceo taj dan.
     */
    private fun buildLedger(
        days: List<DayResult>,
        profile: UserProfile,
        target: Int,
        tdee: Double,
        tissueDensity: Double
    ): TdeeLedger {
        val logged = days.filter { it.status != DayStatus.NOT_LOGGED }
        if (logged.isEmpty()) return TdeeLedger()

        val surplus = logged.filter { it.energyBalance > 0 }.sumOf { it.energyBalance }
        val deficit = logged.filter { it.energyBalance < 0 }.sumOf { it.energyBalance }
        val net = surplus + deficit

        val missDays = logged.filter { it.outcome == DayOutcome.MISS }
        // Šteta = koliko su ti dani odstupili od CILJA, ne od održavanja.
        val missCostKcal = missDays.sumOf { it.deviation }
        // Ali su i dalje nosili svoj bilans prema održavanju.
        val missStillKcal = missDays.sumOf { it.energyBalance }

        val (actualChange, actualSpan) = actualWeightChange(profile, logged.first().date, logged.last().date)

        return TdeeLedger(
            surplusKcal = surplus,
            deficitKcal = deficit,
            daysAboveTdee = logged.count { it.energyBalance > 0 },
            daysBelowTdee = logged.count { it.energyBalance < 0 },
            netKg = net / UserProfile.KCAL_PER_KG,
            netKgComposition = net / tissueDensity,
            plannedNetKg = logged.size * (target - tdee) / UserProfile.KCAL_PER_KG,
            missCostKg = missCostKcal / UserProfile.KCAL_PER_KG,
            missDaysStillGainedKg = missStillKcal / UserProfile.KCAL_PER_KG,
            missDaysBelowTdee = missDays.count { it.energyBalance < 0 },
            actualWeightChangeKg = actualChange,
            actualSpanDays = actualSpan
        )
    }

    /**
     * Stvarna promena na vagi u posmatranom periodu — provera realnosti za procenu iz unosa.
     *
     * Koristi do 7-dnevne prosjeke na oba kraja (dnevna merenja su prešumna za
     * pojedinačne tačke) i meri razmak između TEŽIŠTA prozora, isto kao TDEE učenje
     * u [UserProfile.calculateLearnedTdeeAdjustment]. Vraća null ako je razmak
     * prekratak da bi signal bio upotrebljiv.
     */
    private fun actualWeightChange(
        profile: UserProfile,
        from: LocalDate,
        to: LocalDate
    ): Pair<Double?, Int> {
        val inWindow = profile.weightHistory
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedBy { it.date }
        if (inWindow.size < 2) return null to 0

        val first = inWindow.first().date
        val last = inWindow.last().date
        val startEntries = inWindow.filter {
            ChronoUnit.DAYS.between(first, it.date) < UserProfile.TDEE_WINDOW_DAYS
        }
        val endEntries = inWindow.filter {
            ChronoUnit.DAYS.between(it.date, last) < UserProfile.TDEE_WINDOW_DAYS
        }
        val startMid = startEntries.map { ChronoUnit.DAYS.between(first, it.date).toDouble() }.average()
        val endMid = endEntries.map { ChronoUnit.DAYS.between(first, it.date).toDouble() }.average()
        val effectiveDays = endMid - startMid
        if (effectiveDays < UserProfile.MIN_EFFECTIVE_TDEE_DAYS) return null to 0

        val change = endEntries.map { it.weightKg }.average() - startEntries.map { it.weightKg }.average()
        return change to effectiveDays.roundToInt()
    }

    /**
     * Trenutni niz: broji unazad od danas dok su dani u cilju.
     * Današnji dan još traje, pa ako danas nije upisan (ili je ispod cilja)
     * niz se meri od juče — inače bi ujutru uvek pisalo 0.
     */
    private fun currentStreak(days: List<DayResult>, today: LocalDate): Int {
        if (days.isEmpty()) return 0
        val byDate = days.associateBy { it.date }
        // Danas se preskače dok se ne "zaključa": još može da se doda hrana.
        val startFrom = if (byDate[today]?.outcome == DayOutcome.HIT) today else today.minusDays(1)

        var streak = 0
        var cursor = startFrom
        while (true) {
            val day = byDate[cursor] ?: break
            if (day.outcome != DayOutcome.HIT) break
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Grupiše uzastopne dane istog ishoda u periode. */
    private fun buildSpans(days: List<DayResult>): List<StreakSpan> {
        val spans = mutableListOf<StreakSpan>()
        var spanStart: DayResult? = null
        var previous: DayResult? = null
        var sum = 0.0

        for (day in days) {
            val start = spanStart
            if (start == null || day.outcome != previous!!.outcome) {
                if (start != null) {
                    spans += StreakSpan(start.date, previous!!.date, previous.outcome, sum)
                }
                spanStart = day
                sum = day.deviation
            } else {
                sum += day.deviation
            }
            previous = day
        }
        spanStart?.let { start ->
            spans += StreakSpan(start.date, previous!!.date, previous.outcome, sum)
        }
        return spans
    }
}
