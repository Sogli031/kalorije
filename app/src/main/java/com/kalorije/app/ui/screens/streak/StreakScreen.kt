package com.kalorije.app.ui.screens.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kalorije.app.data.model.GoalMode
import com.kalorije.app.ui.components.CollapsiblePanel
import com.kalorije.app.ui.components.GlassCard
import com.kalorije.app.ui.components.ScreenHeader
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppDimens
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.DayOutcome
import com.kalorije.app.util.DayResult
import com.kalorije.app.util.DayStatus
import com.kalorije.app.util.StreakAnalysis
import com.kalorije.app.util.StreakSpan
import com.kalorije.app.util.TdeeLedger
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.")

/**
 * Boja dana zavisi od ISHODA, ne samo od smera: dan iznad cilja je zelen kad se goji,
 * jer i dalje radi za cilj. Nijansa razdvaja tačno pogođen dan od korisnog odstupanja.
 */
private fun dayColor(day: DayResult): Color = when (day.outcome) {
    DayOutcome.NOT_LOGGED -> AppColors.SurfaceLight
    DayOutcome.HIT -> if (day.status == DayStatus.ON_TARGET) AppColors.Success else AppColors.PrimaryDark
    DayOutcome.MISS -> if (day.status == DayStatus.OVER) AppColors.Calories else AppColors.Warning
}

private fun outcomeColor(outcome: DayOutcome, direction: DayStatus): Color = when (outcome) {
    DayOutcome.NOT_LOGGED -> AppColors.SurfaceLight
    DayOutcome.HIT -> AppColors.Success
    DayOutcome.MISS -> if (direction == DayStatus.OVER) AppColors.Calories else AppColors.Warning
}

private fun dayLabel(day: DayResult, goalMode: GoalMode): String = when {
    day.outcome == DayOutcome.NOT_LOGGED -> "Neupisano"
    day.status == DayStatus.ON_TARGET -> "U cilju"
    day.outcome == DayOutcome.HIT && day.status == DayStatus.OVER -> "Iznad cilja (radi za bulk)"
    day.outcome == DayOutcome.HIT -> "Ispod cilja (radi za cut)"
    day.status == DayStatus.OVER -> if (goalMode == GoalMode.CUT) "Višak — koči skidanje" else "Višak"
    else -> if (goalMode == GoalMode.BULK) "Manjak — koči gojenje" else "Manjak"
}

/** Naziv smera koji odmaže datom cilju, za natpise u UI-u. */
private fun missLabel(goalMode: GoalMode): String = when (goalMode) {
    GoalMode.BULK -> "Manjak"
    GoalMode.CUT -> "Višak"
    GoalMode.MAINTENANCE -> "Odstupanje"
}

@Composable
fun StreakScreen(
    viewModel: StreakViewModel = viewModel(factory = StreakViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val analysis = uiState.analysis

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(AppDimens.paddingMedium)
        ) {
            item {
                ScreenHeader(
                    title = "Doslednost",
                    subtitle = "Koliko dana držiš cilj i šta te to košta"
                )
            }

            if (analysis.trackedDays == 0) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (uiState.isLoading) "Učitavanje…"
                            else "Još nema upisanih dana. Unesi hranu bar nekoliko dana da bi analiza imala smisla.",
                            style = AppTypography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
                return@LazyColumn
            }

            item { StreakHeroCard(analysis) }
            item { BreakdownCard(analysis) }
            item { HeatStrip(analysis) }
            item { TdeeLedgerCard(analysis) }
            item { ImpactCard(analysis) }

            if (analysis.worstSpans.isNotEmpty()) {
                item {
                    Text(
                        text = "Najskuplji periodi",
                        style = AppTypography.titleMedium,
                        color = AppColors.TextPrimary
                    )
                }
                items(analysis.worstSpans) { span -> WorstSpanRow(span, analysis.goalMode) }
            }

            item { DailyEffectPanel(analysis) }
            item { MethodologyPanel(analysis) }
            item { Spacer(Modifier.height(AppDimens.paddingLarge)) }
        }
    }
}

@Composable
private fun StreakHeroCard(analysis: StreakAnalysis) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = if (analysis.currentStreak > 0) AppColors.Success else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (analysis.currentStreak > 0) AppColors.Success else AppColors.TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.size(AppDimens.paddingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${analysis.currentStreak} ${daysWord(analysis.currentStreak)}",
                    style = AppTypography.numberLarge,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "trenutni niz u cilju",
                    style = AppTypography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${analysis.bestStreak}",
                    style = AppTypography.titleLarge,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "najduži niz",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun BreakdownCard(analysis: StreakAnalysis) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
            Text(
                text = "${analysis.adherencePercent}% dana radi za cilj",
                style = AppTypography.titleMedium,
                color = AppColors.TextPrimary
            )
            Text(
                text = "Cilj ${analysis.target} kcal · tolerancija ±${analysis.toleranceKcal} kcal · " +
                    "period ${analysis.trackedDays} ${daysWord(analysis.trackedDays)}",
                style = AppTypography.bodySmall,
                color = AppColors.TextMuted
            )
            if (analysis.hasHelpfulDirection) {
                Text(
                    text = when (analysis.goalMode) {
                        GoalMode.BULK -> "Cilj je gojenje, pa se dani iznad cilja broje kao pogođeni — " +
                            "kalorije idu u pravom smeru. Tačno u toleranciju je palo " +
                            "${analysis.exactTargetPercent}% dana."
                        else -> "Cilj je skidanje, pa se dani ispod cilja broje kao pogođeni. " +
                            "Tačno u toleranciju je palo ${analysis.exactTargetPercent}% dana."
                    },
                    style = AppTypography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.height(AppDimens.paddingXSmall))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
                StatChip("U cilju", analysis.onTargetDays, AppColors.Success, Modifier.weight(1f))
                StatChip(
                    label = "Iznad",
                    value = analysis.overDays,
                    color = if (analysis.goalMode == GoalMode.BULK) AppColors.PrimaryDark else AppColors.Calories,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Ispod",
                    value = analysis.underDays,
                    color = if (analysis.goalMode == GoalMode.CUT) AppColors.PrimaryDark else AppColors.Warning,
                    modifier = Modifier.weight(1f)
                )
                StatChip("Neupisano", analysis.notLoggedDays, AppColors.TextMuted, Modifier.weight(1f))
            }
            if (analysis.loggingCoveragePercent < 80) {
                Text(
                    text = "Upisano je samo ${analysis.loggingCoveragePercent}% dana — brojke ispod su zato " +
                        "donja granica stvarnog odstupanja.",
                    style = AppTypography.bodySmall,
                    color = AppColors.Warning
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(AppColors.SurfaceLight)
            .padding(vertical = AppDimens.paddingSmall, horizontal = AppDimens.paddingXSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "$value", style = AppTypography.titleMedium, color = color)
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = AppColors.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Traka poslednjih ~10 nedelja: jedan kvadratić po danu, boja po statusu.
 */
@Composable
private fun HeatStrip(analysis: StreakAnalysis) {
    val recent = analysis.days.takeLast(70)
    if (recent.isEmpty()) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
            Text(
                text = "Poslednjih ${recent.size} ${daysWord(recent.size)}",
                style = AppTypography.labelLarge,
                color = AppColors.TextSecondary
            )
            recent.chunked(10).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day -> DayCell(day) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
                LegendDot("U cilju", AppColors.Success)
                if (analysis.hasHelpfulDirection) {
                    LegendDot(
                        label = if (analysis.goalMode == GoalMode.BULK) "Iznad (ok)" else "Ispod (ok)",
                        color = AppColors.PrimaryDark
                    )
                }
                LegendDot(
                    label = missLabel(analysis.goalMode),
                    color = if (analysis.goalMode == GoalMode.CUT) AppColors.Calories else AppColors.Warning
                )
                LegendDot("Neupisano", AppColors.SurfaceLight)
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.size(4.dp))
        Text(text = label, style = AppTypography.labelSmall, color = AppColors.TextMuted)
    }
}

@Composable
private fun DayCell(day: DayResult) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(dayColor(day))
    )
}

/**
 * Glavni odgovor na pitanje „koliko bi trebalo da sam u plusu": zbir svega
 * u odnosu na održavanje, pa razdvojeno šta su promašeni dani stvarno odneli.
 */
@Composable
private fun TdeeLedgerCard(analysis: StreakAnalysis) {
    val l = analysis.ledger
    if (analysis.loggedDays == 0) return

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = if (l.netKg >= 0) AppColors.Success else AppColors.Warning
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
            Text(
                text = "Zbir prema održavanju",
                style = AppTypography.titleMedium,
                color = AppColors.TextPrimary
            )
            Text(
                text = "Sve upisane kalorije (${analysis.loggedDays} ${daysWord(analysis.loggedDays)}) " +
                    "merene prema TDEE ${analysis.tdee.roundToInt()} kcal.",
                style = AppTypography.bodySmall,
                color = AppColors.TextMuted
            )

            Spacer(Modifier.height(AppDimens.paddingXSmall))
            Text(
                text = String.format("%+.2f kg", l.netKg),
                style = AppTypography.numberLarge,
                color = if (l.netKg >= 0) AppColors.Success else AppColors.Warning
            )
            Text(
                text = "toliko bi trebalo da si u plusu po unosu " +
                    "(do ${String.format("%+.2f", l.netKgComposition)} kg uz kompoziciju tkiva)",
                style = AppTypography.bodySmall,
                color = AppColors.TextSecondary
            )

            Spacer(Modifier.height(AppDimens.paddingXSmall))
            InfoRow(
                label = "Iznad održavanja · ${l.daysAboveTdee} ${daysWord(l.daysAboveTdee)}",
                value = "+${l.surplusKcal.roundToInt()} kcal",
                color = AppColors.Success
            )
            InfoRow(
                label = "Ispod održavanja · ${l.daysBelowTdee} ${daysWord(l.daysBelowTdee)}",
                value = "${l.deficitKcal.roundToInt()} kcal",
                color = AppColors.Warning
            )
            InfoRow(
                label = "Da si svaki dan pogodio cilj",
                value = String.format("%+.2f kg", l.plannedNetKg),
                color = AppColors.TextSecondary
            )

            if (analysis.missDays > 0) {
                Spacer(Modifier.height(AppDimens.paddingXSmall))
                MissCostText(analysis, l)
            }

            l.actualWeightChangeKg?.let { actual ->
                Spacer(Modifier.height(AppDimens.paddingXSmall))
                InfoRow(
                    label = "Stvarno na vagi (${l.actualSpanDays} ${daysWord(l.actualSpanDays)})",
                    value = String.format("%+.2f kg", actual),
                    color = AppColors.TextPrimary
                )
                Text(
                    text = reconciliationText(actual, l),
                    style = AppTypography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Poenta koju korisnik traži: dan ispod cilja nije izgubljen dan ako je i dalje
 * iznad održavanja — razdvoji „propušteno u odnosu na plan" od „i dalje dobijeno".
 */
@Composable
private fun MissCostText(analysis: StreakAnalysis, l: TdeeLedger) {
    val costText = String.format("%.2f kg", abs(l.missCostKg))
    val stillText = String.format("%+.2f kg", l.missDaysStillGainedKg)

    Text(
        text = buildString {
            append("${analysis.missDays} ${daysWord(analysis.missDays)} ")
            append(missLabel(analysis.goalMode).lowercase())
            append(" te je u odnosu na cilj koštalo oko $costText. ")
            if (l.missDaysBelowTdee == 0) {
                append("Ali nijedan od njih nije bio ispod održavanja — ti dani su ti ipak doneli ")
                append("$stillText, samo sporije nego po planu.")
            } else {
                append("Od toga je ${l.missDaysBelowTdee} ${daysWord(l.missDaysBelowTdee)} bilo i ispod ")
                append("održavanja; svi promašeni dani zajedno su neto dali $stillText.")
            }
        },
        style = AppTypography.bodyMedium,
        color = AppColors.TextSecondary
    )
}

private fun reconciliationText(actual: Double, l: TdeeLedger): String {
    val diff = actual - l.netKg
    return when {
        abs(diff) < 0.5 -> "Vaga se slaže sa procenom iz unosa — kalorije i TDEE su ti realni."
        diff > 0 -> "Vaga pokazuje ${String.format("%.2f", diff)} kg više od procene. Ili je TDEE " +
            "niži nego što app misli, ili je deo toga voda/glikogen, ili se unos potcenjuje."
        else -> "Vaga pokazuje ${String.format("%.2f", abs(diff))} kg manje od procene. Ili je TDEE " +
            "viši nego što app misli, ili se unos precenjuje (porcije, ulje pri kuvanju)."
    }
}

@Composable
private fun ImpactCard(analysis: StreakAnalysis) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
            Text(
                text = "Uticaj na napredak",
                style = AppTypography.titleMedium,
                color = AppColors.TextPrimary
            )

            val dev = analysis.totalDeviationKcal
            InfoRow(
                label = "Ukupno odstupanje od cilja",
                value = "${if (dev >= 0) "+" else ""}${dev.roundToInt()} kcal",
                color = if (dev >= 0) AppColors.Calories else AppColors.Warning
            )
            InfoRow(
                label = "U kilogramima",
                value = String.format("%+.2f kg", analysis.deviationKg),
                color = AppColors.TextPrimary
            )
            InfoRow(
                label = "Planirano za taj period",
                value = String.format("%+.2f kg", analysis.plannedChangeKg),
                color = AppColors.TextSecondary
            )
            InfoRow(
                label = "Očekivano po stvarnom unosu",
                value = String.format("%+.2f kg", analysis.expectedChangeKg),
                color = AppColors.TextSecondary
            )

            Spacer(Modifier.height(AppDimens.paddingXSmall))
            InfoRow(
                label = "Prosečan dnevni bilans (vs. TDEE ${analysis.tdee.roundToInt()})",
                value = "${if (analysis.avgDailyBalance >= 0) "+" else ""}${analysis.avgDailyBalance.roundToInt()} kcal",
                color = AppColors.TextSecondary
            )

            val delay = analysis.delayDays
            Spacer(Modifier.height(AppDimens.paddingXSmall))
            Text(
                text = when {
                    analysis.goalMode == GoalMode.MAINTENANCE ->
                        "Cilj je održavanje, pa nema plana tempa u odnosu na koji bi se merilo kašnjenje. " +
                            "Odstupanje iznad znači koliko si ukupno odlutao od nule."
                    delay == null -> "Nema definisanog dnevnog viška/manjka, pa se kašnjenje ne može izračunati."
                    delay > 0.5 -> "Promašeni dani su te usporili za oko ${fmtDays(delay)} — toliko duže ti " +
                        "treba do istog rezultata."
                    delay < -0.5 -> "Zapravo si ispred plana za oko ${fmtDays(abs(delay))}."
                    else -> "Praktično si tačno na planu — odstupanja se međusobno ponište."
                },
                style = AppTypography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = AppTypography.bodySmall, color = AppColors.TextMuted)
        Text(text = value, style = AppTypography.bodyMedium, color = color)
    }
}

@Composable
private fun WorstSpanRow(span: StreakSpan, goalMode: GoalMode) {
    val color = outcomeColor(span.outcome, span.direction)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = color
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${span.start.format(dateFormatter)} – ${span.end.format(dateFormatter)}",
                    style = AppTypography.bodyMedium,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "${missLabel(goalMode)} · ${span.days} ${daysWord(span.days)}",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (span.totalDeviation >= 0) "+" else ""}${span.totalDeviation.roundToInt()} kcal",
                    style = AppTypography.bodyMedium,
                    color = color
                )
                Text(
                    text = String.format("%+.2f kg", span.totalDeviation / 7700.0),
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
        }
    }
}

/**
 * Dan po dan: koliko je taj dan teorijski pomerio kilažu.
 */
@Composable
private fun DailyEffectPanel(analysis: StreakAnalysis) {
    val recent = analysis.days.reversed().take(60)
    if (recent.isEmpty()) return

    CollapsiblePanel(title = "Dan po dan — uticaj na kilažu") {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingSmall)) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.paddingXSmall)) {
                    Text(
                        text = "Bilans se meri prema TDEE (${analysis.tdee.roundToInt()} kcal), ne prema cilju — " +
                            "održavanje je nulta linija za kilažu.",
                        style = AppTypography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = "Za tvoj profil 1 kg promene košta ~${analysis.tissueKcalPerKg.roundToInt()} kcal " +
                            "(nemasna masa ${analysis.leanPercentRange.first.roundToInt()}–" +
                            "${analysis.leanPercentRange.second.roundToInt()}% promene).",
                        style = AppTypography.bodySmall,
                        color = AppColors.TextMuted
                    )
                }
            }

            recent.forEach { day -> DayEffectRow(day, analysis.goalMode) }

            if (analysis.days.size > 60) {
                Text(
                    text = "Prikazano poslednjih 60 od ${analysis.days.size} dana.",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun DayEffectRow(day: DayResult, goalMode: GoalMode) {
    val notLogged = day.status == DayStatus.NOT_LOGGED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(AppColors.SurfaceLight)
            .padding(horizontal = AppDimens.paddingSmall, vertical = AppDimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dayColor(day))
        )
        Spacer(Modifier.size(AppDimens.paddingSmall))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.date.format(dateFormatter),
                style = AppTypography.bodyMedium,
                color = AppColors.TextPrimary
            )
            Text(
                text = if (notLogged) "nema unosa"
                else "${day.calories.roundToInt()} kcal · ${dayLabel(day, goalMode)}",
                style = AppTypography.labelSmall,
                color = AppColors.TextMuted
            )
        }
        if (!notLogged) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%+.3f kg", day.estimatedKgChange),
                    style = AppTypography.bodyMedium,
                    color = if (day.energyBalance >= 0) AppColors.Calories else AppColors.Protein
                )
                Text(
                    text = "${if (day.energyBalance >= 0) "+" else ""}${day.energyBalance.roundToInt()} kcal " +
                        "· do ${String.format("%+.3f", day.estimatedKgChangeComposition)} kg",
                    style = AppTypography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
        } else {
            Text(text = "—", style = AppTypography.bodyMedium, color = AppColors.TextMuted)
        }
    }
}

@Composable
private fun MethodologyPanel(analysis: StreakAnalysis) {
    CollapsiblePanel(title = "Kako se ovo računa") {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = buildString {
                    appendLine("• Dan je „u cilju\" ako je unos u opsegu ${analysis.target} ± ${analysis.toleranceKcal} kcal (10% cilja, minimum 100 kcal).")
                    appendLine()
                    appendLine(
                        when (analysis.goalMode) {
                            GoalMode.BULK -> "• Pošto je cilj gojenje, dan IZNAD cilja se broji kao pogođen i ne prekida niz — kalorije su išle u pravom smeru, samo brže od plana. Niz prekida samo manjak."
                            GoalMode.CUT -> "• Pošto je cilj skidanje, dan ISPOD cilja se broji kao pogođen i ne prekida niz. Niz prekida samo višak."
                            GoalMode.MAINTENANCE -> "• Pošto je cilj održavanje, oba smera odmiču od nule, pa i višak i manjak prekidaju niz."
                        }
                    )
                    appendLine()
                    appendLine("• Koristi se TRENUTNI cilj za sve dane u istoriji — app ne čuva istoriju ciljeva, pa ako si menjao cilj, stariji dani su ocenjeni po današnjem merilu.")
                    appendLine()
                    appendLine("• Neupisani dani se ne broje ni kao promašaj ni u zbir odstupanja. Za njih se prosto ne zna šta je pojedeno, pa je ukupan uticaj donja granica.")
                    appendLine()
                    appendLine("• Kalorije se u kilograme pretvaraju sa 7700 kcal/kg (masno tkivo). Čist mišić je jeftiniji (~2000 kcal/kg), pa je pri dobijanju stvarna promena obično nešto veća od prikazane.")
                    appendLine()
                    appendLine("• „Usporenje\" = odstupanje u kg podeljeno planiranim dnevnim tempom. To je procena reda veličine, ne precizno merenje — stvarna težina zavisi i od vode, treninga i tačnosti unosa.")
                    appendLine()
                    appendLine("DNEVNA PROMENA KILAŽE")
                    appendLine()
                    appendLine("• Za svaki dan se računa bilans prema TDEE-u (${analysis.tdee.roundToInt()} kcal), pa se deli energetskom cenom kilograma. Prva brojka koristi 7700 kcal/kg, druga („do…\") uzima u obzir kompoziciju tkiva.")
                    appendLine()
                    appendLine("• Kompozicija se računa Forbes formulom p = 10.4 / (10.4 + masa masti), uz iste dodatke za trening i TRT koje app već koristi za P-ratio. Zatim, po Hall-u (2008): 1 kg masti ≈ 9440 kcal, 1 kg nemasne mase ≈ 1816 kcal (velikim delom voda, pa je mnogo jeftinija). Otuda cena kilograma za tvoj profil ≈ ${analysis.tissueKcalPerKg.roundToInt()} kcal.")
                    appendLine()
                    append("• VAŽNO: ovo je TEORIJSKA promena tkiva, ne ono što ćeš videti na vagi. Dnevne oscilacije od vode, glikogena i sadržaja creva su ±0.5–1.5 kg i potpuno zakrivaju efekat jednog dana. Brojka ima smisla tek kada se sabere preko nedelja — zato i postoji zbir iznad.")
                },
                style = AppTypography.bodySmall,
                color = AppColors.TextSecondary
            )
        }
    }
}

private fun daysWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "dana"
        mod10 == 1 -> "dan"
        else -> "dana"
    }
}

private fun fmtDays(days: Double): String {
    val rounded = days.roundToInt()
    return "$rounded ${daysWord(rounded)}"
}
