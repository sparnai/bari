package com.sparnai.bari.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.TransportMode
import com.sparnai.bari.core.title
import com.sparnai.bari.ui.components.Pill
import com.sparnai.bari.ui.components.PriceChart
import com.sparnai.bari.ui.components.TrendPill
import com.sparnai.bari.ui.theme.LocalPriceColors

private val sections = listOf(
    TransportMode.FLIGHT to "Skrydžiai",
    TransportMode.TRAIN to "Traukiniai",
    TransportMode.BUS to "Autobusai",
)

/** All tracked legs, grouped into flights, trains and buses. Prices are for all passengers. */
@Composable
fun FlightsScreen(state: AppState, onOpen: (String) -> Unit) {
    val pax = state.trip.passengers.coerceAtLeast(1)
    val byMode = state.trip.segments
        .sortedWith(compareBy<Segment> { it.date }.thenBy { it.timeFrom ?: it.timeTo ?: "" })
        .groupBy { it.mode }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.trip.segments.isEmpty()) {
            item { EmptyCard("Nėra stebimų reisų. Paspauskite „Pridėti“.") }
        }
        if (pax > 1) {
            item(key = "pax") {
                Text(
                    "Kainos rodomos ${Format.passengers(pax)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        sections.forEach { (mode, title) ->
            val list = byMode[mode].orEmpty()
            if (list.isEmpty()) return@forEach
            val open = mode.name !in collapsed
            item(key = "h_${mode.name}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { collapsed = if (open) collapsed + mode.name else collapsed - mode.name }
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(modeIcon(mode), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$title (${list.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (open) "Suskleisti" else "Išskleisti",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (open) {
                items(list, key = { it.id }) { segment ->
                    SegmentCard(segment, state, pax, onClick = { onOpen(segment.id) })
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun SegmentCard(segment: Segment, state: AppState, pax: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val record = state.records[segment.id]?.takeIf { it.signature == null || it.signature == segment.signature }
    val leg = RouteMath.leg(segment, record)
    val price = leg.price
    val target = segment.targetPrice
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(segment.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val flight = leg.fare?.flightNumber?.takeIf { it.isNotBlank() && it != segment.carrier }
                    val times = leg.departure?.let { "${Format.time(it)}–${Format.time(leg.arrival)}" }
                    val stops = leg.fare?.stops?.takeIf { it > 0 }?.let { "$it persėd." }
                    Text(
                        listOfNotNull(Format.dateShort(segment.date), leg.fare?.carrier ?: segment.carrier, flight, times, stops)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.total(price, pax), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (pax > 1 && price != null) {
                        Text("${Format.euro(price)} / asm.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                    TrendPill(Format.times(price, pax), Format.times(leg.previousPrice, pax))
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
                Pill(segment.source.title, scheme.secondaryContainer, scheme.onSecondaryContainer)
                if (record?.lowest != null) {
                    Pill("Žemiausia ${Format.total(record.lowest, pax)}", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
                }
                if (target != null) {
                    val reached = price != null && price <= target
                    val colors = LocalPriceColors.current
                    Pill(
                        "Tikslas ${Format.total(target, pax)}",
                        if (reached) colors.downContainer else scheme.surfaceContainerHighest,
                        if (reached) colors.down else scheme.onSurfaceVariant,
                        Icons.Filled.Flag,
                    )
                }
            }
            if (record?.lastError != null) {
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
            Text(
                "Tikrinta ${Format.ago(record?.lastChecked)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
