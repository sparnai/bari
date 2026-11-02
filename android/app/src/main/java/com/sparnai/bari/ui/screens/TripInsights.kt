package com.sparnai.bari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.AdviceLevel
import com.sparnai.bari.core.BuyAdvice
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.PriceAdvice
import com.sparnai.bari.core.PricePoint
import com.sparnai.bari.core.Priority
import com.sparnai.bari.ui.components.PriceChart
import com.sparnai.bari.ui.theme.LocalPriceColors
import java.time.Instant
import java.time.ZoneId

/** Price of the best whole trip over time – the same kind of chart as for single tickets. */
@Composable
fun TripChartCard(series: List<PricePoint>, passengers: Int, priority: Priority) {
    val scheme = MaterialTheme.colorScheme
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (priority == Priority.FASTEST) "Greičiausios kelionės kaina" else "Geriausios kelionės kaina",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Nuvykimas + grįžimas, ${Format.passengers(passengers)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            PriceChart(series, Modifier.fillMaxWidth().height(140.dp))
            if (series.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ChartStat("Dabar", Format.euro(series.last().p))
                    ChartStat("Žemiausia", Format.euro(series.minOf { it.p }))
                    ChartStat("Aukščiausia", Format.euro(series.maxOf { it.p }))
                }
                val since = Instant.ofEpochMilli(series.first().t).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    "Stebima nuo ${Format.dayMonth(since)} · ${series.size} kainos taškai",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "When to buy?" – forecast from general fare patterns and the trip's own price trend, plus tips. */
@Composable
fun BuyAdviceCard(advice: BuyAdvice) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalPriceColors.current
    var showTips by rememberSaveable { mutableStateOf(false) }
    val (container, accent) = when (advice.level) {
        AdviceLevel.BUY_NOW -> colors.warnContainer to colors.warn
        AdviceLevel.GOOD_TIME -> colors.downContainer to colors.down
        AdviceLevel.WAIT -> scheme.surfaceContainerHigh to scheme.primary
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kada pirkti?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = accent)
            }
            Text(advice.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("Prognozė: ${advice.forecast}", style = MaterialTheme.typography.bodyMedium)
            advice.details.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "Prognozė remiasi bendrais aviabilietų kainų dėsningumais ir jūsų kainų istorija – tai ne garantija.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            TextButton(onClick = { showTips = !showTips }, modifier = Modifier.padding(0.dp)) {
                Text(if (showTips) "Slėpti patarimus" else "Patarimai, kada bilietai pigiausi (${PriceAdvice.tips.size})")
            }
            if (showTips) {
                PriceAdvice.tips.forEach { tip ->
                    Text("💡 $tip", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Rank journeys by price or by travel time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityToggle(priority: Priority, onChange: (Priority) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Prioritetas:", style = MaterialTheme.typography.labelLarge)
        FilterChip(
            selected = priority == Priority.CHEAPEST,
            onClick = { onChange(Priority.CHEAPEST) },
            label = { Text("Pigiausias") },
        )
        FilterChip(
            selected = priority == Priority.FASTEST,
            onClick = { onChange(Priority.FASTEST) },
            label = { Text("Greičiausias") },
        )
    }
}
