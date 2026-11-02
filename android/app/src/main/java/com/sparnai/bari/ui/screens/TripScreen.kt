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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import com.sparnai.bari.core.TransportMode
import com.sparnai.bari.core.TripHistory
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.Direction
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.Leg
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.PriceAdvice
import com.sparnai.bari.core.PricePoint
import com.sparnai.bari.core.Priority
import com.sparnai.bari.core.PriceScope
import com.sparnai.bari.core.RouteOption
import com.sparnai.bari.core.RouteSummary
import com.sparnai.bari.ui.components.LayoverPill
import com.sparnai.bari.ui.components.PriceChart
import com.sparnai.bari.ui.components.Pill
import com.sparnai.bari.ui.components.TrendPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sparnai.bari.ui.theme.LocalPriceColors

@Composable
fun TripScreen(
    state: AppState,
    onOpenSegment: (String) -> Unit,
    onPassengers: (Int) -> Unit,
    onPriceScope: (PriceScope) -> Unit,
    onEditRoute: (RouteOption?) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onToggleExcluded: (String) -> Unit,
    onModeIncluded: (TransportMode, Boolean) -> Unit,
    onPriority: (Priority) -> Unit,
    onShare: (RouteSummary) -> Unit,
) {
    val scope = state.settings.priceScope
    val excluded = state.settings.excludedRoutes
    val allowed = state.settings.allowedModes
    val priority = state.settings.priority
    val plan = remember(state.trip, state.records, scope, excluded, allowed, priority) {
        RouteMath.plan(state.trip, state.records, scope, excluded, allowed, priority)
    }
    // Price history of the best trip and of each journey, rebuilt off the main thread.
    val charts by produceState(TripCharts(), state.trip, state.records, state.settings) {
        value = withContext(Dispatchers.Default) {
            TripCharts(
                best = TripHistory.bestTotal(state.trip, state.records, state.settings),
                routes = state.trip.routes.associate { it.id to TripHistory.route(it, state.trip, state.records, scope) },
            )
        }
    }
    val departure = state.trip.segments.mapNotNull { RouteMath.parseDate(it.date) }.minOrNull()
    val advice = remember(charts.best, departure) { departure?.let { PriceAdvice.forecast(it, charts.best) } }
    val bestIds = listOfNotNull(plan.bestOut, plan.bestBack, plan.bestRound).map { it.route.id }.toSet()
    var confirmDelete by remember { mutableStateOf<RouteOption?>(null) }
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
                    best = summary.route.id in bestIds,
                    passengers = state.trip.passengers,
                    onOpenSegment = onOpenSegment,
                    onEdit = { onEditRoute(summary.route) },
                    onDelete = { confirmDelete = summary.route },
                    onToggleExcluded = { onToggleExcluded(summary.route.id) },
                    history = charts.routes[summary.route.id].orEmpty(),
                    priority = priority,
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
        item(key = "chart") { TripChartCard(charts.best, state.trip.passengers, priority) }
        advice?.let { a -> item(key = "advice") { BuyAdviceCard(a) } }
        item(key = "priority") { PriorityToggle(priority, onPriority) }
        item(key = "scope") { ScopeToggle(scope, onPriceScope) }
        item(key = "modes") { ModeToggle(state.settings.includeBuses, state.settings.includeTrains, onModeIncluded) }
        if (state.trip.routes.isEmpty()) {
            item {
                Text(
                    "Dar nėra maršrutų. Pridėkite reisus skiltyje „Transportas“ ir sujunkite juos į maršrutą.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        section("out", "Nuvykimas į Barį", outDate?.let { Format.dateShort(it) }, plan.outbound)
        hiddenNote("out", plan.hidden.filter { it.route.direction == Direction.OUT }, allowed)
        section("back", "Grįžimas į Vilnių", backDate?.let { Format.dateShort(it) }, plan.back)
        hiddenNote("back", plan.hidden.filter { it.route.direction == Direction.BACK }, allowed)
        section("round", "Bilietas į abi puses", null, plan.round)
        hiddenNote("round", plan.hidden.filter { it.route.direction == Direction.ROUND }, allowed)
        item(key = "add") {
            OutlinedButton(onClick = { onEditRoute(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Naujas maršruto variantas")
            }
        }
        item(key = "pad") { Spacer(Modifier.height(72.dp)) }
    }

    confirmDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Pašalinti variantą?") },
            text = {
                Text(
                    "„${route.name}“ bus pašalintas. Jei norite jį tik paslėpti iš palyginimo, rinkitės „Neįtraukti į palyginimą“. " +
                        "Ištrintus numatytuosius variantus galima grąžinti nustatymuose.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDeleteRoute(route.id) }) { Text("Pašalinti") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Atšaukti") } },
        )
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
            val what = (if (state.settings.priceScope == PriceScope.FLIGHTS) "tik skrydžiai" else "pilna kaina") +
                if (state.settings.priority == Priority.FASTEST) " · greičiausia" else " · pigiausia"
            Text(
                "Visa kelionė (nuvykimas + grįžimas), ${Format.passengers(pax)} · $what",
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

/** Price histories shown on the trip screen. */
data class TripCharts(
    val best: List<PricePoint> = emptyList(),
    val routes: Map<String, List<PricePoint>> = emptyMap(),
)

/** "Paslėpta 3 variantai su autobusais" under a section. */
private fun LazyListScope.hiddenNote(key: String, hidden: List<RouteSummary>, allowed: Set<TransportMode>) {
    if (hidden.isEmpty()) return
    val modes = hidden.flatMap { r -> r.legs.map { it.segment.mode } }.filter { it !in allowed }.toSet()
    val what = listOfNotNull(
        "autobusais".takeIf { TransportMode.BUS in modes },
        "traukiniais".takeIf { TransportMode.TRAIN in modes },
    ).joinToString(" ir ")
    item(key = "hidden_$key") {
        Text(
            "Paslėpta variantų su $what: ${hidden.size} (įjunkite viršuje „Įtraukti“)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Which ground transport may be part of the plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(buses: Boolean, trains: Boolean, onChange: (TransportMode, Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Įtraukti:", style = MaterialTheme.typography.labelLarge)
        FilterChip(
            selected = buses,
            onClick = { onChange(TransportMode.BUS, !buses) },
            label = { Text("Autobusai") },
            leadingIcon = { Icon(if (buses) Icons.Filled.Check else Icons.Filled.DirectionsBus, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = trains,
            onClick = { onChange(TransportMode.TRAIN, !trains) },
            label = { Text("Traukiniai") },
            leadingIcon = { Icon(if (trains) Icons.Filled.Check else Icons.Filled.Train, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

/** Switch between the full journey price and plane tickets only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeToggle(scope: PriceScope, onChange: (PriceScope) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Skaičiuoti:", style = MaterialTheme.typography.labelLarge)
            FilterChip(
                selected = scope == PriceScope.FULL,
                onClick = { onChange(PriceScope.FULL) },
                label = { Text("Pilna kaina") },
            )
            FilterChip(
                selected = scope == PriceScope.FLIGHTS,
                onClick = { onChange(PriceScope.FLIGHTS) },
                label = { Text("Tik skrydžiai") },
            )
        }
        Text(
            if (scope == PriceScope.FULL) {
                "Skrydžiai, traukiniai, autobusai ir persėdimai tarp stočių ir oro uostų. Nakvynė ir bagažas neįskaičiuoti."
            } else {
                "Tik lėktuvų bilietai. Autobusai, traukiniai ir persėdimai rodomi pilkai ir į sumą neįeina."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "→ Nuvykimas: Iš Kauno per Bergamą – 381,82 €" (wraps instead of being cut off). */
@Composable
private fun HeroLine(arrow: String, what: String, summary: RouteSummary?) {
    if (summary == null) return
    Text(
        "$arrow $what: ${summary.route.name} – ${Format.euro(summary.total)}" +
            (summary.durationMinutes?.let { " · ${Format.duration(it)}" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RouteCard(
    summary: RouteSummary,
    best: Boolean,
    passengers: Int,
    onOpenSegment: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleExcluded: () -> Unit,
    onShare: () -> Unit,
    history: List<PricePoint>,
    priority: Priority,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (summary.excluded) 0.55f else 1f),
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
                            Direction.OUT -> if (priority == Priority.FASTEST) "Greičiausias nuvykimas" else "Pigiausias nuvykimas"
                            Direction.BACK -> if (priority == Priority.FASTEST) "Greičiausias grįžimas" else "Pigiausias grįžimas"
                            Direction.ROUND -> "Pigiausias bilietas į abi puses"
                        }
                        Pill(badge, scheme.secondary, scheme.onSecondary, Icons.Filled.EmojiEvents)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(summary.route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (summary.excluded) {
                        Text("Neįtraukta į palyginimą", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                    summary.durationMinutes?.let {
                        Text("⏱ Kelionė trunka ${Format.duration(it)}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
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
                        DropdownMenuItem(
                            text = { Text(if (summary.excluded) "Įtraukti į palyginimą" else "Neįtraukti į palyginimą") },
                            onClick = { menu = false; onToggleExcluded() },
                        )
                        DropdownMenuItem(text = { Text("Redaguoti") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Pašalinti") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (summary.route.extraCostLabel != null) {
                val text = if (summary.route.extraCost > 0 && summary.scope == PriceScope.FULL) {
                    "Įskaičiuota ${Format.total(summary.route.extraCost, passengers)}: ${summary.route.extraCostLabel}"
                } else if (summary.route.extraCost > 0) {
                    "Neįskaičiuota ${Format.total(summary.route.extraCost, passengers)}: ${summary.route.extraCostLabel}"
                } else {
                    "Neįskaičiuota: ${summary.route.extraCostLabel}"
                }
                Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            if (history.size >= 2) {
                PriceChart(history, Modifier.fillMaxWidth().height(40.dp), showDots = false)
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
            .alpha(if (leg.included) 1f else 0.5f)
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
            val times = if (leg.departure != null) Format.timeRange(leg.departure, leg.arrival) else null
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
            if (leg.included) {
                TrendPill(Format.times(leg.price, passengers), Format.times(leg.previousPrice, passengers))
            } else {
                Text("neįskaičiuota", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
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
