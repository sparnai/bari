package com.sparnai.bari.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.Layover
import com.sparnai.bari.core.LayoverLevel
import com.sparnai.bari.core.PricePoint
import com.sparnai.bari.ui.theme.LocalPriceColors

/** Small rounded label with an icon. */
@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Green "−5 €" when cheaper, red "+5 €" when more expensive, nothing otherwise. */
@Composable
fun TrendPill(current: Double?, previous: Double?, modifier: Modifier = Modifier) {
    if (current == null || previous == null || current == previous) return
    val colors = LocalPriceColors.current
    val diff = current - previous
    if (diff < 0) {
        Pill(Format.delta(diff), colors.downContainer, colors.down, Icons.Filled.TrendingDown, modifier)
    } else {
        Pill(Format.delta(diff), colors.upContainer, colors.up, Icons.Filled.TrendingUp, modifier)
    }
}

@Composable
fun LayoverPill(layover: Layover, modifier: Modifier = Modifier) {
    val colors = LocalPriceColors.current
    val scheme = MaterialTheme.colorScheme
    val where = Format.city(layover.at)
    val time = Format.duration(layover.minutes)
    val change = if (layover.airportChange) " · kitas oro uostas" else ""
    when (layover.level) {
        LayoverLevel.OK -> Pill("Persėdimas $where · $time$change", scheme.surfaceContainerHigh, scheme.onSurfaceVariant, Icons.Filled.CheckCircle, modifier)
        LayoverLevel.TIGHT -> Pill("Trumpas persėdimas $where · $time$change", colors.warnContainer, colors.warn, Icons.Filled.Schedule, modifier)
        LayoverLevel.RISKY -> Pill("Rizikinga: tik $time $where$change", colors.upContainer, colors.up, Icons.Filled.Warning, modifier)
        LayoverLevel.IMPOSSIBLE -> Pill("Nespėsite persėsti ($where)", scheme.errorContainer, scheme.onErrorContainer, Icons.Filled.Error, modifier)
        LayoverLevel.LONG -> Pill("Ilgas laukimas $where · $time$change", scheme.surfaceContainerHigh, scheme.onSurfaceVariant, Icons.Filled.HourglassBottom, modifier)
        LayoverLevel.OVERNIGHT -> Pill("Nakvynė $where · $time$change", scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Filled.Bedtime, modifier)
        LayoverLevel.STAY -> Pill("Viešnagė: $where · ${layover.minutes / (24 * 60)} d.", scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Filled.BeachAccess, modifier)
    }
}

/**
 * Price history line chart. Lowest point is marked green, last point with the primary colour.
 */
@Composable
fun PriceChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    showDots: Boolean = true,
) {
    val line = MaterialTheme.colorScheme.primary
    val low = LocalPriceColors.current.down
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Istorija kaupiama – grafikas atsiras po kelių patikrinimų",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Canvas(modifier) {
        val minP = points.minOf { it.p }
        val maxP = points.maxOf { it.p }
        val minT = points.first().t
        val maxT = points.last().t
        val padY = 6.dp.toPx()
        val span = (maxP - minP).takeIf { it > 0.0 } ?: 1.0
        val tSpan = (maxT - minT).takeIf { it > 0L } ?: 1L
        fun x(t: Long, index: Int): Float =
            if (maxT == minT) size.width * index / (points.size - 1) else ((t - minT).toFloat() / tSpan) * size.width
        fun y(p: Double): Float = if (maxP == minP) size.height / 2 else
            (padY + (1 - ((p - minP) / span).toFloat()) * (size.height - 2 * padY))

        // Grid: min / max guides
        listOf(y(maxP), y(minP)).forEach { gy ->
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }

        // Step line: the price stays until the next check.
        val path = Path()
        points.forEachIndexed { i, pt ->
            val px = x(pt.t, i)
            val py = y(pt.p)
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, y(points[i - 1].p))
                path.lineTo(px, py)
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0.02f))))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (showDots) {
            val lowIndex = points.indices.minByOrNull { points[it].p } ?: 0
            drawCircle(low, radius = 4.5.dp.toPx(), center = Offset(x(points[lowIndex].t, lowIndex), y(points[lowIndex].p)))
            val last = points.lastIndex
            drawCircle(line, radius = 4.5.dp.toPx(), center = Offset(x(points[last].t, last), y(points[last].p)))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
    }
}
