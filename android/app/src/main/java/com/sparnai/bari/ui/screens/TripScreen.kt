package com.sparnai.bari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import com.sparnai.bari.core.TransportMode
import com.sparnai.bari.core.TripPlan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.Direction
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.Leg
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.RouteOption
import com.sparnai.bari.core.RouteSummary
import com.sparnai.bari.ui.components.LayoverPill
import com.sparnai.bari.ui.components.Pill
import com.sparnai.bari.ui.components.TrendPill
import com.sparnai.bari.ui.theme.LocalPriceColors

@Composable
fun TripScreen(
    state: AppState,
    onOpenSegment: (String) -> Unit,
    onPassengers: (Int) -> Unit,
    onEditRoute: (RouteOption?) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onShare: (RouteSummary) -> Unit,
) {
    val plan = remember(state.trip, state.records) { RouteMath.plan(state.trip, state.records) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    fun LazyListScope.section(key: String, title: String, subtitle: String?, list: List<RouteSummary>) {
        if (list.isEmpty()) return
        val open = key !in collapsed
        item(key = "h_$key") {
            SectionHeader(title, subtitle, list.size, open) {
                collapsed = if (open) collapsed + key else collapsed - key
            }
        }
        if (open) {
            items(list, key = { it.route.id }) { summary ->
                RouteCard(
                    summary = summary,
                    rank = list.indexOf(summary),
                    passengers = state.trip.passengers,
                    onOpenSegment = onOpenSegment,
                    onEdit = { onEditRoute(summary.route) },
                    onDelete = { onDeleteRoute(summary.route.id) },
                    onShare = { onShare(summary) },
                )
            }
        }
    }
    val outDate = plan.outbound.firstNotNullOfOrNull { it.legs.firstOrNull()?.segment?.date }
    val backDate = plan.back.firstNotNullOfOrNull { it.legs.lastOrNull()?.segment?.date }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") { TripHero(state, plan, onPassengers) }
        if (state.trip.routes.isEmpty()) {
            item {
                Text(
                    "Dar nėra maršrutų. Pridėkite reisus skiltyje „Transportas“ ir sujunkite juos į maršrutą.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        section("out", "Nuvykimas į Barį", outDate?.let { Format.dateShort(it) }, plan.outbound)
        section("back", "Grįžimas į Vilnių", backDate?.let { Format.dateShort(it) }, plan.back)
        section("round", "Bilietas į abi puses", null, plan.round)
        item(key = "add") {
            OutlinedButton(onClick = { onEditRoute(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Naujas maršruto variantas")
            }
        }
        item(key = "pad") { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(subtitle, "$count variantai").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (open) "Suskleisti" else "Išskleisti",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TripHero(state: AppState, plan: TripPlan, onPassengers: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pax = state.trip.passengers
    val dates = state.trip.segments.mapNotNull { RouteMath.parseDate(it.date) }
    val start = dates.minOrNull()
    val end = dates.maxOrNull()
    val onHero = scheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.trip.name, style = MaterialTheme.typography.headlineSmall, color = onHero, fontWeight = FontWeight.Bold)
            if (start != null && end != null) {
                Text("${Format.dateShort(start)} – ${Format.dateShort(end)}", style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.9f))
                Text(Format.countdown(start), style = MaterialTheme.typography.labelLarge, color = onHero)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Visa kelionė (nuvykimas + grįžimas), ${Format.passengers(pax)}",
                style = MaterialTheme.typography.labelMedium,
                color = onHero.copy(alpha = 0.85f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.euro(plan.bestTotal), style = MaterialTheme.typography.displaySmall, color = onHero, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                TrendPill(plan.bestTotal, plan.previousBestTotal)
            }
            val bestTotal = plan.bestTotal
            if (pax > 1 && bestTotal != null) {
                Text("${Format.euro(bestTotal / pax)} / asm.", style = MaterialTheme.typography.labelMedium, color = onHero.copy(alpha = 0.85f))
            }
            when {
                plan.bestTotal == null -> Text("Laukiama kainų…", style = MaterialTheme.typography.bodyMedium, color = onHero)
                plan.roundTripWins -> HeroLine("⇄", "Į abi puses", plan.bestRound)
                else -> {
                    HeroLine("→", "Nuvykimas", plan.bestOut)
                    HeroLine("←", "Grįžimas", plan.bestBack)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = onHero, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Keleiviai", style = MaterialTheme.typography.labelLarge, color = onHero, modifier = Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { onPassengers(pax - 1) },
                    enabled = pax > 1,
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.Remove, contentDescription = "Mažiau keleivių", modifier = Modifier.size(18.dp)) }
                Text("$pax", color = onHero, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
                FilledTonalIconButton(
                    onClick = { onPassengers(pax + 1) },
                    enabled = pax < 9,
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "Daugiau keleivių", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/** "→ Nuvykimas: Iš Kauno per Bergamą – 381,82 €" (wraps instead of being cut off). */
@Composable
private fun HeroLine(arrow: String, what: String, summary: RouteSummary?) {
    if (summary == null) return
    Text(
        "$arrow $what: ${summary.route.name} – ${Format.euro(summary.total)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RouteCard(
    summary: RouteSummary,
    rank: Int,
    passengers: Int,
    onOpenSegment: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val best = rank == 0 && summary.total != null
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = if (best) {
            CardDefaults.elevatedCardColors(containerColor = scheme.secondaryContainer)
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (best) {
                        val badge = when (summary.route.direction) {
                            Direction.OUT -> "Pigiausias nuvykimas"
                            Direction.BACK -> "Pigiausias grįžimas"
                            Direction.ROUND -> "Pigiausias bilietas į abi puses"
                        }
                        Pill(badge, scheme.secondary, scheme.onSecondary, Icons.Filled.EmojiEvents)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(summary.route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (summary.total != null) Format.euro(summary.total) else "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (passengers > 1 && summary.perPerson != null) {
                        Text("${Format.euro(summary.perPerson)} / asm.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                    TrendPill(summary.total, summary.previousTotal)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Daugiau") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Dalintis") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("Redaguoti") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Pašalinti") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (summary.route.extraCostLabel != null) {
                val text = if (summary.route.extraCost > 0) {
                    "Įskaičiuota ${Format.total(summary.route.extraCost, passengers)}: ${summary.route.extraCostLabel}"
                } else {
                    "Neįskaičiuota: ${summary.route.extraCostLabel}"
                }
                Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            summary.legs.forEachIndexed { index, leg ->
                LegRow(leg, passengers, onClick = { onOpenSegment(leg.segment.id) })
                val layover = summary.layovers.getOrNull(index)
                if (index < summary.legs.lastIndex && layover != null) {
                    Box(Modifier.padding(start = 36.dp)) { LayoverPill(layover) }
                }
            }
            if (summary.missing.isNotEmpty()) {
                Text(
                    "Trūksta kainų: ${summary.missing.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPriceColors.current.warn,
                )
            }
            summary.route.note?.takeIf { it.isNotBlank() }?.let { note ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LegRow(leg: Leg, passengers: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val seg = leg.segment

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (seg.mode == TransportMode.FLIGHT) scheme.primaryContainer else scheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modeIcon(seg.mode),
                contentDescription = null,
                tint = if (seg.mode == TransportMode.FLIGHT) scheme.onPrimaryContainer else scheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${Format.city(seg.origin)} → ${Format.city(seg.dest)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val flight = leg.fare?.flightNumber?.takeIf { it.isNotBlank() }
            val times = if (leg.departure != null) "${Format.time(leg.departure)}–${Format.time(leg.arrival)}" else null
            val stops = leg.fare?.stops?.takeIf { it > 0 }?.let { "$it persėd." }
            Text(
                listOfNotNull(Format.dateShort(seg.date), flight, times, stops).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.total(leg.price, passengers), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            TrendPill(Format.times(leg.price, passengers), Format.times(leg.previousPrice, passengers))
        }
    }
}

fun modeIcon(mode: TransportMode): ImageVector = when (mode) {
    TransportMode.FLIGHT -> Icons.Filled.Flight
    TransportMode.TRAIN -> Icons.Filled.Train
    TransportMode.BUS -> Icons.Filled.DirectionsBus
}

@Composable
fun EmptyCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
