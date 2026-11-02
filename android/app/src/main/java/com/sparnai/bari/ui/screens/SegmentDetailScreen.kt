@file:OptIn(ExperimentalMaterial3Api::class)

package com.sparnai.bari.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.DayFare
import com.sparnai.bari.core.FareSource
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.title
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.round2
import com.sparnai.bari.ui.components.PriceChart
import com.sparnai.bari.ui.components.SectionTitle
import com.sparnai.bari.ui.components.TrendPill
import com.sparnai.bari.ui.theme.LocalPriceColors

@Composable
fun SegmentDetailScreen(
    segment: Segment,
    state: AppState,
    loadFlexible: suspend (Segment) -> Result<List<DayFare>>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveToDate: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val record = state.records[segment.id]?.takeIf { it.signature == null || it.signature == segment.signature }
    val leg = RouteMath.leg(segment, record)
    val usedIn = state.trip.routes.filter { segment.id in it.segments }
    val pax = state.trip.passengers.coerceAtLeast(1)
    var confirmDelete by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf<DayFare?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${place(segment.origin)} → ${place(segment.dest)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        RouteMath.parseDate(segment.date)?.let { Format.dateLong(it) } ?: segment.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Format.total(leg.price, pax),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        TrendPill(Format.times(leg.price, pax), Format.times(leg.previousPrice, pax))
                    }
                    if (pax > 1 && leg.price != null) {
                        Text(
                            "${Format.passengers(pax).replaceFirstChar { it.uppercase() }} · ${Format.euro(leg.price)} / asm.",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                    val flight = leg.fare?.flightNumber?.takeIf { it.isNotBlank() }
                    val times = leg.departure?.let { "${Format.time(it)} → ${Format.time(leg.arrival)}" }
                    Text(
                        listOfNotNull(segment.carrier, flight, times).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    val window = listOfNotNull(segment.timeFrom?.let { "nuo $it" }, segment.timeTo?.let { "iki $it" })
                    val filters = listOfNotNull(
                        window.takeIf { it.isNotEmpty() }?.let { "išvykimas ${it.joinToString(" ")}" },
                        segment.airlines.takeIf { it.isNotEmpty() }?.let { "tik ${it.joinToString(", ")}" },
                        when (segment.maxStops) {
                            null -> null
                            0 -> "tik tiesioginiai"
                            else -> "iki ${segment.maxStops} persėd."
                        },
                        segment.maxHours?.let { "iki $it val." },
                        segment.returnDate?.let { "atgal ${Format.dateShort(it)}" },
                    )
                    if (filters.isNotEmpty()) {
                        Text(
                            "Ieškoma: ${filters.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Text(
                        "${segment.source.title} · tikrinta ${Format.ago(record?.lastChecked)}" +
                            (record?.lastError?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { uri.openUri(RouteMath.bookingUrl(segment, leg.fare, state.trip.passengers)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (segment.source) {
                            FareSource.RYANAIR -> "Rezervuoti Ryanair"
                            FareSource.GOOGLE -> "Google Flights"
                            FareSource.TRENITALIA -> "Trenitalia"
                            FareSource.FLIXBUS -> "FlixBus"
                            FareSource.MANUAL -> "Ieškoti"
                        },
                    )
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Redaguoti")
                }
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Pašalinti")
                }
            }
        }

        item {
          Column {
            SectionTitle("Kainų istorija")
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PriceChart(record?.history.orEmpty(), Modifier.fillMaxWidth().height(160.dp))
                    val history = record?.history.orEmpty()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Žemiausia", Format.total(record?.lowest, pax))
                        Stat("Aukščiausia", Format.total(record?.highest, pax))
                        Stat("Vidurkis", Format.total(history.takeIf { it.isNotEmpty() }?.let { h -> round2(h.sumOf { it.p } / h.size) }, pax))
                        Stat("Patikrinta", "${history.size}×")
                    }
                    val target = segment.targetPrice
                    if (target != null) {
                        val price = leg.price
                        val colors = LocalPriceColors.current
                        val text = when {
                            price == null -> "Tikslinė kaina ${Format.total(target, pax)}"
                            price <= target -> "🎯 Tikslinė kaina ${Format.total(target, pax)} pasiekta!"
                            else -> "Iki tikslinės kainos ${Format.total(target, pax)} trūksta ${Format.total(round2(price - target), pax)}"
                        }
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (price != null && price <= target) colors.down else scheme.onSurface)
                    }
                }
            }
          }
        }

        if (segment.source == FareSource.RYANAIR) {
            item {
                Column {
                    SectionTitle("Gretimos dienos (±3 d.)")
                    FlexibleDates(segment, pax, loadFlexible, onPick = { moveTo = it })
                }
            }
        }

        if (usedIn.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Naudojama maršrutuose")
                    Text(usedIn.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Pašalinti skrydį?") },
            text = { Text("„${segment.label}“ ir jo kainų istorija bus ištrinti. Maršrutai, kuriuose jis naudojamas, taps nepilni.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Pašalinti") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Atšaukti") } },
        )
    }

    moveTo?.let { day ->
        AlertDialog(
            onDismissRequest = { moveTo = null },
            title = { Text("Stebėti kitą dieną?") },
            text = {
                Text(
                    "Perkelti „${segment.label}“ į ${Format.dateShort(day.day)} (${Format.total(day.price, pax)})? " +
                        "Šio skrydžio kainų istorija prasidės iš naujo.",
                )
            },
            confirmButton = { TextButton(onClick = { moveTo = null; onMoveToDate(day.day) }) { Text("Perkelti") } },
            dismissButton = { TextButton(onClick = { moveTo = null }) { Text("Atšaukti") } },
        )
    }
}

private fun place(code: String): String = Format.city(code).let { if (it == code) it else "$it ($code)" }

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlexibleDates(
    segment: Segment,
    pax: Int,
    load: suspend (Segment) -> Result<List<DayFare>>,
    onPick: (DayFare) -> Unit,
) {
    var days by remember(segment.signature) { mutableStateOf<List<DayFare>?>(null) }
    var error by remember(segment.signature) { mutableStateOf<String?>(null) }
    LaunchedEffect(segment.signature) {
        load(segment)
            .onSuccess { days = it }
            .onFailure { error = it.message ?: "Nepavyko įkelti" }
    }
    val scheme = MaterialTheme.colorScheme
    val colors = LocalPriceColors.current
    when {
        error != null -> Text("Nepavyko įkelti gretimų dienų: $error", style = MaterialTheme.typography.bodySmall, color = scheme.error)
        days == null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Ieškoma pigiausių dienų…", style = MaterialTheme.typography.bodySmall)
        }
        days.isNullOrEmpty() -> Text("Ryanair nepateikė kainų šioms dienoms.", style = MaterialTheme.typography.bodySmall)
        else -> Column {
            val list = days.orEmpty()
            val cheapest = list.mapNotNull { it.price }.minOrNull()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.day }) { day ->
                    val date = RouteMath.parseDate(day.day)
                    val selected = day.day == segment.date
                    val isCheapest = day.price != null && day.price == cheapest
                    val available = day.price != null && !day.soldOut
                    OutlinedCard(
                        onClick = { if (available && !selected) onPick(day) },
                        enabled = available,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            when {
                                selected -> scheme.primary
                                isCheapest -> colors.down
                                else -> scheme.outlineVariant
                            },
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isCheapest) colors.downContainer else scheme.surface,
                        ),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp).width(56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(date?.let { Format.weekdayShort(it) } ?: "", style = MaterialTheme.typography.labelSmall)
                            Text(date?.dayOfMonth?.toString() ?: day.day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    day.soldOut -> "išparduota"
                                    day.price == null -> "—"
                                    else -> Format.total(day.price, pax)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = if (isCheapest) colors.down else scheme.onSurface,
                            )
                        }
                    }
                }
            }
            Text(
                "Paspauskite kitą dieną, kad perkeltumėte stebėjimą.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
