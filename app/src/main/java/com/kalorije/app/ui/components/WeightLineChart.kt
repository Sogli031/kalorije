package com.kalorije.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalorije.app.data.model.WeightEntry
import com.kalorije.app.ui.theme.AppColors
import java.time.format.DateTimeFormatter

@Composable
fun WeightLineChart(
    entries: List<WeightEntry>,
    goalWeight: Double? = null,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    val sortedEntries = remember(entries) { entries.sortedBy { it.date } }

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "chart_animation"
    )

    val density = LocalDensity.current
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#8B949E")
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }

    // Pre-kalkulisani chart podaci — ne računaj na svaki frame
    val chartCalculations = remember(sortedEntries, goalWeight) {
        if (sortedEntries.size < 2) return@remember null

        val allWeights = buildList {
            addAll(sortedEntries.map { it.weightKg })
            goalWeight?.let { add(it) }
        }
        val minWeight = allWeights.minOrNull() ?: 0.0
        val maxWeight = allWeights.maxOrNull() ?: 100.0
        val weightRange = (maxWeight - minWeight).coerceAtLeast(2.0)
        val yMin = minWeight - weightRange * 0.1
        val yMax = maxWeight + weightRange * 0.1

        ChartCalculations(yMin = yMin, yMax = yMax, yRange = yMax - yMin)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val paddingLeft = 45.dp.toPx()
        val paddingBottom = 25.dp.toPx()
        val paddingTop = 10.dp.toPx()
        val paddingRight = 10.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        if (sortedEntries.size < 2) {
            // Just show single point
            val x = paddingLeft + chartWidth / 2
            val y = paddingTop + chartHeight / 2
            drawCircle(
                color = AppColors.Primary,
                radius = 6.dp.toPx(),
                center = Offset(x, y)
            )
            return@Canvas
        }

        val calc = chartCalculations ?: return@Canvas
        val yMin = calc.yMin
        val yMax = calc.yMax
        val yRange = calc.yRange

        // Draw horizontal grid lines
        val gridLineCount = 4
        for (i in 0..gridLineCount) {
            val y = paddingTop + (chartHeight * i / gridLineCount)
            drawLine(
                color = AppColors.GlassBorder,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )

            // Draw Y axis labels
            val weightValue = yMax - (yRange * i / gridLineCount)
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", weightValue),
                5.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // Draw goal line if set
        goalWeight?.let { goal ->
            if (goal in yMin..yMax) {
                val goalY = paddingTop + ((yMax - goal) / yRange * chartHeight).toFloat()
                drawLine(
                    color = AppColors.Success,
                    start = Offset(paddingLeft, goalY),
                    end = Offset(size.width - paddingRight, goalY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }
        }

        // Calculate point positions - use effective (normalized) weight
        val points = sortedEntries.mapIndexed { index, entry ->
            val x = paddingLeft + (chartWidth * index / (sortedEntries.size - 1))
            val y = paddingTop + ((yMax - entry.weightKg) / yRange * chartHeight).toFloat()
            Offset(x, y)
        }

        // Draw line connecting points
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val animatedIndex = (points.size * animatedProgress).toInt().coerceAtMost(i)
                    if (animatedIndex >= i) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
            }
            drawPath(
                path = path,
                color = AppColors.Primary,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // Draw data points
        val pointsToShow = (points.size * animatedProgress).toInt().coerceAtMost(points.size)
        for (i in 0 until pointsToShow) {
            val point = points[i]
            // White center
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = point
            )
            // Colored border
            drawCircle(
                color = AppColors.Primary,
                radius = 5.dp.toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Draw X axis labels (first and last dates)
        if (sortedEntries.isNotEmpty()) {
            val firstDate = sortedEntries.first().date.format(dateFormatter)
            val lastDate = sortedEntries.last().date.format(dateFormatter)

            drawContext.canvas.nativeCanvas.drawText(
                firstDate,
                paddingLeft,
                size.height - 5.dp.toPx(),
                textPaint
            )

            if (sortedEntries.size > 1) {
                val lastTextWidth = textPaint.measureText(lastDate)
                drawContext.canvas.nativeCanvas.drawText(
                    lastDate,
                    size.width - paddingRight - lastTextWidth,
                    size.height - 5.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}

private data class ChartCalculations(
    val yMin: Double,
    val yMax: Double,
    val yRange: Double
)
