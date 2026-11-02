@file:OptIn(ExperimentalMaterial3Api::class)

package com.sparnai.bari.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.sparnai.bari.core.FareSource
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
    var source by remember { mutableStateOf(existing?.source ?: FareSource.RYANAIR) }
    var origin by remember { mutableStateOf(existing?.origin.orEmpty()) }
    var dest by remember { mutableStateOf(existing?.dest.orEmpty()) }
    var date by remember { mutableStateOf(existing?.date ?: defaultDate.toString()) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var carrier by remember { mutableStateOf(existing?.carrier.orEmpty()) }
    var timeFrom by remember { mutableStateOf(existing?.timeFrom.orEmpty()) }
    var timeTo by remember { mutableStateOf(existing?.timeTo.orEmpty()) }
    var target by remember { mutableStateOf(money(existing?.targetPrice)) }
    var price by remember { mutableStateOf(money(existing?.price)) }
    var flight by remember { mutableStateOf(existing?.flight.orEmpty()) }
    var departure by remember { mutableStateOf(existing?.departure.orEmpty()) }
    var arrival by remember { mutableStateOf(existing?.arrival.orEmpty()) }

    val originOk = iataRegex.matches(origin)
    val destOk = iataRegex.matches(dest)
    val parsedDate = RouteMath.parseDate(date)
    fun timeOk(t: String) = t.isBlank() || RouteMath.parseTime(t) != null
    val manual = source == FareSource.MANUAL
    val priceOk = !manual || parseMoney(price) != null
    val targetOk = target.isBlank() || parseMoney(target) != null
    val valid = originOk && destOk && parsedDate != null && priceOk && targetOk &&
        timeOk(timeFrom) && timeOk(timeTo) && timeOk(departure) && timeOk(arrival)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Naujas skrydis" else "Redaguoti skrydį") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !manual, onClick = { source = FareSource.RYANAIR }, label = { Text("Ryanair (automatiškai)") })
                    FilterChip(selected = manual, onClick = { source = FareSource.MANUAL }, label = { Text("Rankinė kaina") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = origin,
                        onValueChange = { origin = it.uppercase().filter(Char::isLetter).take(3) },
                        label = { Text("Iš (IATA)") },
                        placeholder = { Text("VNO") },
                        isError = origin.isNotEmpty() && !originOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dest,
                        onValueChange = { dest = it.uppercase().filter(Char::isLetter).take(3) },
                        label = { Text("Į (IATA)") },
                        placeholder = { Text("BRI") },
                        isError = dest.isNotEmpty() && !destOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { parsedDate?.let { date = it.minusDays(1).toString() } }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Diena anksčiau")
                    }
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it.take(10) },
                        label = { Text("Data (MMMM-MM-DD)") },
                        isError = parsedDate == null,
                        supportingText = { parsedDate?.let { Text(Format.dateLong(it)) } },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { parsedDate?.let { date = it.plusDays(1).toString() } }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Diena vėliau")
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Pavadinimas (nebūtina)") },
                    placeholder = { Text("${Format.city(origin.ifBlank { "VNO" })} → ${Format.city(dest.ifBlank { "BRI" })}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!manual) {
                    Text("Išvykimo laiko langas (nebūtina) – pvz. tik ryto reisai", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(timeFrom, { timeFrom = it }, "Nuo (HH:mm)", Modifier.weight(1f))
                        TimeField(timeTo, { timeTo = it }, "Iki (HH:mm)", Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Kaina asmeniui, €") },
                            isError = !priceOk,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = flight,
                            onValueChange = { flight = it },
                            label = { Text("Reisas") },
                            placeholder = { Text("OS917") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(departure, { departure = it }, "Išvyksta", Modifier.weight(1f))
                        TimeField(arrival, { arrival = it }, "Atvyksta", Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text("Vežėjas (nebūtina)") },
                    placeholder = { Text(if (manual) "Austrian Airlines" else "Ryanair") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Tikslinė kaina, € (pranešti, kai pasieks)") },
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
                    val id = existing?.id ?: RouteMath.newSegmentId(origin, dest, date, trip.segments.map { it.id })
                    val segment = Segment(
                        id = id,
                        label = label.trim().ifBlank { "${Format.city(origin)} → ${Format.city(dest)}" },
                        origin = origin,
                        dest = dest,
                        date = parsedDate.toString(),
                        source = source,
                        carrier = carrier.trim().ifBlank { if (manual) null else "Ryanair" },
                        timeFrom = if (manual) null else timeFrom.normalizedTime(),
                        timeTo = if (manual) null else timeTo.normalizedTime(),
                        targetPrice = parseMoney(target),
                        price = if (manual) parseMoney(price) else null,
                        flight = if (manual) flight.trim().ifBlank { null } else null,
                        departure = if (manual) departure.normalizedTime() else null,
                        arrival = if (manual) arrival.normalizedTime() else null,
                    )
                    onSave(segment, existing?.id)
                },
            ) { Text("Išsaugoti") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Atšaukti") } },
    )
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
                Text("Skrydžiai (eilės tvarka pagal datą)", style = MaterialTheme.typography.labelMedium)
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
                        label = { Text("Papildomai, €") },
                        isError = !extraOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = extraLabel,
                        onValueChange = { extraLabel = it },
                        label = { Text("Už ką") },
                        placeholder = { Text("nakvynė") },
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
