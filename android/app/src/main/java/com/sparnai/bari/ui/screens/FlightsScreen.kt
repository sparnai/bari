package com.sparnai.bari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.Segment
import com.sparnai.bari.ui.components.Pill
import com.sparnai.bari.ui.components.PriceChart
import com.sparnai.bari.ui.components.TrendPill
import com.sparnai.bari.ui.theme.LocalPriceColors

@Composable
fun FlightsScreen(state: AppState, onOpen: (String) -> Unit) {
    val grouped = state.trip.segments
        .sortedWith(compareBy<Segment> { it.date }.thenBy { it.isManual })
        .groupBy { it.date }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (grouped.isEmpty()) {
            item { EmptyCard("Nėra stebimų skrydžių. Paspauskite „+“, kad pridėtumėte.") }
        }
        grouped.forEach { (date, segments) ->
            item(key = "h_$date") {
                Text(
                    RouteMath.parseDate(date)?.let { Format.dateLong(it) } ?: date,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(segments, key = { it.id }) { segment ->
                SegmentCard(segment, state, onClick = { onOpen(segment.id) })
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun SegmentCard(segment: Segment, state: AppState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val record = state.records[segment.id]?.takeIf { it.signature == null || it.signature == segment.signature }
    val leg = RouteMath.leg(segment, record)
    val price = leg.price
    val target = segment.targetPrice
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (segment.isManual) Icons.Filled.EditNote else Icons.Filled.FlightTakeoff,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val flight = leg.fare?.flightNumber?.takeIf { it.isNotBlank() }
                    val times = leg.departure?.let { "${Format.time(it)}–${Format.time(leg.arrival)}" }
                    Text(
                        listOfNotNull(segment.carrier, flight, times).joinToString(" · ").ifBlank { "${segment.origin} → ${segment.dest}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.euro(price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TrendPill(price, leg.previousPrice)
                }
            }
            if (record != null && record.history.size >= 2) {
                PriceChart(
                    points = record.history,
                    showDots = false,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (segment.isManual) {
                    Pill("Rankinė kaina", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                } else if (record?.lowest != null) {
                    Pill("Žemiausia ${Format.euro(record.lowest)}", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
                }
                if (target != null) {
                    val reached = price != null && price <= target
                    val colors = LocalPriceColors.current
                    Pill(
                        "Tikslas ${Format.euro(target)}",
                        if (reached) colors.downContainer else scheme.surfaceContainerHighest,
                        if (reached) colors.down else scheme.onSurfaceVariant,
                        Icons.Filled.Flag,
                    )
                }
            }
            if (record?.lastError != null && !segment.isManual) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = LocalPriceColors.current.warn, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        record.lastError + if (price != null) " · rodoma paskutinė žinoma kaina" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalPriceColors.current.warn,
                    )
                }
            }
            if (!segment.isManual) {
                Text(
                    "Tikrinta ${Format.ago(record?.lastChecked)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
