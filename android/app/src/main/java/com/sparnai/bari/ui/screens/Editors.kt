@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sparnai.bari.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sparnai.bari.core.Direction
import com.sparnai.bari.core.FareSource
import com.sparnai.bari.core.TransportMode
import com.sparnai.bari.core.mode
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.RouteOption
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.TripConfig
import java.time.LocalDate

private fun parseMoney(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

private fun money(value: Double?): String = value?.let {
    if (it % 1.0 == 0.0) it.toLong().toString() else String.format(java.util.Locale.US, "%.2f", it)
}.orEmpty()

private val iataRegex = Regex("^[A-Z]{3}$")

/** Add / edit a tracked leg. [existing] null means a new segment. */
@Composable
fun SegmentEditorDialog(
    existing: Segment?,
    trip: TripConfig,
    onDismiss: () -> Unit,
    onSave: (segment: Segment, previousId: String?) -> Unit,
) {
    val defaultDate = trip.segments.mapNotNull { RouteMath.parseDate(it.date) }.minOrNull() ?: LocalDate.now().plusDays(30)
    var source by remember {
        mutableStateOf(existing?.source?.takeIf { it != FareSource.MANUAL } ?: FareSource.RYANAIR)
    }
    var origin by remember { mutableStateOf(existing?.origin.orEmpty()) }
    var dest by remember { mutableStateOf(existing?.dest.orEmpty()) }
    var date by remember { mutableStateOf(existing?.date ?: defaultDate.toString()) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var timeFrom by remember { mutableStateOf(existing?.timeFrom.orEmpty()) }
    var timeTo by remember { mutableStateOf(existing?.timeTo.orEmpty()) }
    var target by remember { mutableStateOf(money(existing?.targetPrice)) }
    var airlines by remember { mutableStateOf(existing?.airlines.orEmpty().joinToString(", ")) }
    var maxStops by remember { mutableStateOf(existing?.maxStops) }
    var returnDate by remember { mutableStateOf(existing?.returnDate.orEmpty()) }
    var maxHours by remember { mutableStateOf(existing?.maxHours) }

    val isFlight = source.mode == TransportMode.FLIGHT
    fun placeOk(v: String) = if (isFlight) iataRegex.matches(v) else v.isNotBlank()
    val originOk = placeOk(origin)
    val destOk = placeOk(dest)
    val parsedDate = RouteMath.parseDate(date)
    val parsedReturn = RouteMath.parseDate(returnDate)
    fun timeOk(t: String) = t.isBlank() || RouteMath.parseTime(t) != null
    val targetOk = target.isBlank() || parseMoney(target) != null
    val returnOk = source != FareSource.GOOGLE || returnDate.isBlank() || (parsedReturn != null && parsedDate != null && !parsedReturn.isBefore(parsedDate))
    val valid = originOk && destOk && parsedDate != null && targetOk && returnOk && timeOk(timeFrom) && timeOk(timeTo)
    val placeHint = when (source) {
        FareSource.TRENITALIA -> "Stotis" to ("Roma Termini" to "Bari Centrale")
        FareSource.FLIXBUS -> "Miestas" to ("Rome" to "Bari")
        else -> "IATA" to ("VNO" to "BRI")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing != null -> "Redaguoti"
                    source.mode == TransportMode.TRAIN -> "Naujas traukinys"
                    source.mode == TransportMode.BUS -> "Naujas autobusas"
                    else -> "Naujas skrydis"
                },
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Kainos šaltinis (tikrinama automatiškai)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FareSource.RYANAIR to "Ryanair",
                        FareSource.GOOGLE to "Kitos aviakompanijos",
                        FareSource.TRENITALIA to "Traukinys",
                        FareSource.FLIXBUS to "Autobusas",
                    ).forEach { (value, text) ->
                        FilterChip(
                            selected = source == value,
                            onClick = {
                                if (source.mode != value.mode) {
                                    origin = ""
                                    dest = ""
                                }
                                source = value
                            },
                            label = { Text(text) },
                        )
                    }
                }
                Text(
                    when (source) {
                        FareSource.RYANAIR -> "Ryanair kainų API."
                        FareSource.GOOGLE -> "Google Flights: Lufthansa, Wizz Air, LOT, Austrian, airBaltic ir kt."
                        FareSource.TRENITALIA -> "Trenitalia (Italijos traukiniai)."
                        FareSource.FLIXBUS -> "FlixBus autobusai."
                        FareSource.MANUAL -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = origin,
                        onValueChange = { origin = if (isFlight) it.uppercase().filter(Char::isLetter).take(3) else it },
                        label = { Text("Iš (${placeHint.first})") },
                        placeholder = { Text(placeHint.second.first) },
                        isError = origin.isNotEmpty() && !originOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = if (isFlight) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dest,
                        onValueChange = { dest = if (isFlight) it.uppercase().filter(Char::isLetter).take(3) else it },
                        label = { Text("Į (${placeHint.first})") },
                        placeholder = { Text(placeHint.second.second) },
                        isError = dest.isNotEmpty() && !destOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = if (isFlight) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                DateField(date, { date = it }, "Data (MMMM-MM-DD)", parsedDate)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Pavadinimas (nebūtina)") },
                    placeholder = { Text("${Format.city(origin.ifBlank { "VNO" })} → ${Format.city(dest.ifBlank { "BRI" })}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Išvykimo laiko langas (nebūtina) – pvz. tik ryto reisai", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(timeFrom, { timeFrom = it }, "Nuo (HH:mm)", Modifier.weight(1f))
                    TimeField(timeTo, { timeTo = it }, "Iki (HH:mm)", Modifier.weight(1f))
                }
                if (source == FareSource.GOOGLE) {
                    OutlinedTextField(
                        value = airlines,
                        onValueChange = { airlines = it.uppercase() },
                        label = { Text("Tik šios aviakompanijos (nebūtina)") },
                        placeholder = { Text("pvz. LO, OS, W6, LH, BT") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Persėdimai", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "Bet kiek", 0 to "Tik tiesioginiai", 1 to "Iki 1").forEach { (value, text) ->
                            FilterChip(selected = maxStops == value, onClick = { maxStops = value }, label = { Text(text) })
                        }
                    }
                    Text("Didžiausia kelionės trukmė", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "Bet kokia", 6 to "≤ 6 val.", 12 to "≤ 12 val.").forEach { (value, text) ->
                            FilterChip(selected = maxHours == value, onClick = { maxHours = value }, label = { Text(text) })
                        }
                    }
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Tikslinė kaina asmeniui, € (pranešti, kai pasieks)") },
                    isError = !targetOk,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val o = origin.trim()
                    val d = dest.trim()
                    val id = existing?.id ?: RouteMath.newSegmentId(o, d, date, trip.segments.map { it.id })
                    val google = source == FareSource.GOOGLE
                    val segment = Segment(
                        id = id,
                        label = label.trim().ifBlank { "${Format.city(o)} → ${Format.city(d)}" },
                        origin = o,
                        dest = d,
                        date = parsedDate.toString(),
                        source = source,
                        carrier = when (source) {
                            FareSource.RYANAIR -> "Ryanair"
                            FareSource.TRENITALIA -> "Trenitalia"
                            FareSource.FLIXBUS -> "FlixBus"
                            else -> existing?.takeIf { it.source == FareSource.GOOGLE }?.carrier
                        },
                        timeFrom = timeFrom.normalizedTime(),
                        timeTo = timeTo.normalizedTime(),
                        targetPrice = parseMoney(target),
                        airlines = if (google) airlines.split(',', ' ', ';').map { it.trim() }.filter { it.length == 2 } else emptyList(),
                        maxStops = if (google) maxStops else null,
                        returnDate = if (google) parsedReturn?.toString() else null,
                        maxHours = if (google) maxHours else null,
                    )
                    onSave(segment, existing?.id)
                },
            ) { Text("Išsaugoti") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Atšaukti") } },
    )
}

@Composable
private fun DateField(value: String, onChange: (String) -> Unit, label: String, parsed: LocalDate?, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { parsed?.let { onChange(it.minusDays(1).toString()) } }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Diena anksčiau")
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(10)) },
            label = { Text(label) },
            isError = parsed == null && !(optional && value.isBlank()),
            supportingText = { parsed?.let { Text(Format.dateLong(it)) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { parsed?.let { onChange(it.plusDays(1).toString()) } }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Diena vėliau")
        }
    }
}

private fun String.normalizedTime(): String? =
    RouteMath.parseTime(this)?.let { String.format(java.util.Locale.US, "%02d:%02d", it.hour, it.minute) }

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == ':' }.take(5)) },
        label = { Text(label) },
        placeholder = { Text("10:00") },
        isError = value.isNotBlank() && RouteMath.parseTime(value) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Create / edit a route option = ordered list of legs. */
@Composable
fun RouteEditorDialog(
    existing: RouteOption?,
    trip: TripConfig,
    onDismiss: () -> Unit,
    onSave: (RouteOption) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var extra by remember { mutableStateOf(money(existing?.extraCost?.takeIf { it > 0 })) }
    var extraLabel by remember { mutableStateOf(existing?.extraCostLabel.orEmpty()) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(existing?.segments.orEmpty()) } }
    var direction by remember { mutableStateOf(existing?.direction ?: Direction.OUT) }
    val ordered = trip.segments.sortedWith(compareBy<Segment> { it.date }.thenBy { it.departure ?: it.timeFrom ?: "" })
    val extraOk = extra.isBlank() || parseMoney(extra) != null
    val valid = name.isNotBlank() && selected.isNotEmpty() && extraOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Naujas maršrutas" else "Redaguoti maršrutą") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Pavadinimas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Direction.OUT to "Nuvykimas", Direction.BACK to "Grįžimas").forEach { (value, text) ->
                        FilterChip(selected = direction == value, onClick = { direction = value }, label = { Text(text) })
                    }
                }
                Text("Skrydžiai, traukiniai, autobusai (eilės tvarka pagal datą)", style = MaterialTheme.typography.labelMedium)
                ordered.forEach { seg ->
                    val checked = seg.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selected.remove(seg.id) else selected.add(seg.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { if (it) selected.add(seg.id) else selected.remove(seg.id) })
                        Column {
                            Text(seg.label, style = MaterialTheme.typography.bodyMedium)
                            Text(Format.dateShort(seg.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = extra,
                        onValueChange = { extra = it },
                        label = { Text("Papildomai asmeniui, €") },
                        isError = !extraOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = extraLabel,
                        onValueChange = { extraLabel = it },
                        label = { Text("Už ką") },
                        placeholder = { Text("pvz. autobusas į oro uostą") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Pastabos") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val order = ordered.map { it.id }
                    val id = existing?.id ?: run {
                        val base = "route_" + System.currentTimeMillis().toString(36)
                        if (trip.routes.none { it.id == base }) base else base + "_2"
                    }
                    onSave(
                        RouteOption(
                            id = id,
                            name = name.trim(),
                            segments = selected.sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                            direction = direction,
                            extraCost = parseMoney(extra) ?: 0.0,
                            extraCostLabel = extraLabel.trim().ifBlank { null },
                            note = note.trim().ifBlank { null },
                        ),
                    )
                },
            ) { Text("Išsaugoti") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Atšaukti") } },
    )
}
