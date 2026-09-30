package com.kalorije.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalorije.app.ui.theme.AppColors
import com.kalorije.app.ui.theme.AppTypography
import com.kalorije.app.util.getLogicalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Glavna kartica sa dnevnim kalorijama: kružni progres levo, brojke desno.
 */
@Composable
fun CalorieRingCard(
    current: Double,
    goal: Int,
    goalIsEstimate: Boolean = false,
    weeklyCurrent: Double? = null,
    weekStart: LocalDate? = null,
    modifier: Modifier = Modifier
) {
    val weeklyGoal = goal * 7
    val ratio = if (goal > 0) (current / goal).toFloat() else 0f
    val overGoal = ratio > 1f
    val remaining = (goal - current).coerceAtLeast(0.0)
    val over = (current - goal).coerceAtLeast(0.0)
    val percent = (ratio * 100).roundToInt()

    val accent = if (overGoal) AppColors.Warning else AppColors.Calories

    GlassCard(
        modifier = modifier,
        bgColor = AppColors.SurfaceRaised,
        accentColor = accent
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DANAS",
                    style = AppTypography.labelLarge.copy(
                        color = accent,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.5.sp,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = if (overGoal) "cilj premašen" else "u okviru cilja",
                    style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalorieRing(
                    ratio = ratio,
                    percent = percent,
                    accent = accent,
                    overGoal = overGoal
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = current.roundToInt().toString(),
                            style = AppTypography.numberLarge.copy(
                                color = AppColors.TextPrimary,
                                fontSize = 34.sp
                            )
                        )
                        Text(
                            text = "kcal",
                            style = AppTypography.labelMedium.copy(color = AppColors.TextMuted),
                            modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
                        )
                    }
                    Text(
                        text = "od $goal kcal" + if (goalIsEstimate) " (procena)" else "",
                        style = AppTypography.bodyMedium
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (overGoal) {
                                "+${over.roundToInt()} kcal preko cilja"
                            } else {
                                "Preostalo ${remaining.roundToInt()} kcal"
                            },
                            style = AppTypography.labelMedium.copy(
                                color = accent,
                                fontWeight = FontWeight.W600
                            )
                        )
                    }
                }
            }

            if (weeklyCurrent != null) {
                WeeklyGoalRow(
                    weeklyCurrent = weeklyCurrent,
                    weeklyGoal = weeklyGoal,
                    weekStart = weekStart
                )
            }

            if (goalIsEstimate) {
                Text(
                    text = "Cilj prema formuli — TDEE se još utvrđuje",
                    style = AppTypography.labelSmall.copy(color = AppColors.Warning)
                )
            }
        }
    }
}

/**
 * Nedeljni presek: ponedeljak — nedelja, cilj je dnevni cilj puta 7.
 * Prikazuje i koliko si trenutno ispod/iznad tempa za protekle dane u nedelji.
 */
@Composable
private fun WeeklyGoalRow(
    weeklyCurrent: Double,
    weeklyGoal: Int,
    weekStart: LocalDate?
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("d.M.") }
    val rangeLabel = weekStart?.let {
        "${it.format(dayFormatter)} – ${it.plusDays(6).format(dayFormatter)}"
    }
    // Koliko dana nedelje je proteklo (uključujući današnji)
    val daysElapsed = weekStart?.let {
        (java.time.temporal.ChronoUnit.DAYS.between(it, getLogicalDate()).toInt() + 1).coerceIn(1, 7)
    }
    val paceGoal = daysElapsed?.let { (weeklyGoal / 7.0) * it }
    val paceDiff = paceGoal?.let { weeklyCurrent - it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceLight.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEDELJA (pon–ned)" + if (rangeLabel != null) "  $rangeLabel" else "",
                style = AppTypography.labelSmall.copy(
                    color = AppColors.TextMuted,
                    letterSpacing = 0.8.sp
                )
            )
            Text(
                text = "${weeklyCurrent.roundToInt()} / $weeklyGoal kcal",
                style = AppTypography.labelMedium.copy(
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.W600
                )
            )
        }

        if (paceDiff != null) {
            val diffRounded = paceDiff.roundToInt()
            Text(
                text = when {
                    diffRounded > 0 -> "+$diffRounded kcal iznad tempa posle $daysElapsed. dana"
                    diffRounded < 0 -> "${-diffRounded} kcal ispod tempa posle $daysElapsed. dana"
                    else -> "Tačno na tempu posle $daysElapsed. dana"
                },
                style = AppTypography.labelSmall.copy(
                    color = if (diffRounded > 0) AppColors.Warning else AppColors.TextMuted
                )
            )
        }
    }
}

@Composable
private fun CalorieRing(
    ratio: Float,
    percent: Int,
    accent: Color,
    overGoal: Boolean
) {
    val animated by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 2f),
        animationSpec = tween(durationMillis = 700),
        label = "calorie_ring"
    )

    Box(
        modifier = Modifier
            .size(104.dp)
            .progressSemantics(ratio.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(104.dp)) {
            val strokeWidth = 10.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Pozadinski prsten
            drawArc(
                color = AppColors.SurfaceLight,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (overGoal) {
                // Pun krug do cilja, pa preliv preko njega
                drawArc(
                    color = AppColors.Calories.copy(alpha = 0.35f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = ((animated - 1f).coerceIn(0f, 1f)) * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = animated.coerceIn(0f, 1f) * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent",
                style = AppTypography.numberLarge.copy(
                    color = AppColors.TextPrimary,
                    fontSize = 26.sp
                )
            )
            Text(
                text = "%",
                style = AppTypography.labelSmall.copy(color = AppColors.TextMuted)
            )
        }
    }
}
