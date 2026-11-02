package com.sparnai.bari.core.cli

import com.sparnai.bari.core.BariJson
import com.sparnai.bari.core.Fare
import com.sparnai.bari.core.FareSource
import com.sparnai.bari.core.Format
import com.sparnai.bari.core.RouteMath
import com.sparnai.bari.core.RouteSummary
import com.sparnai.bari.core.Segment
import com.sparnai.bari.core.SegmentRecord
import com.sparnai.bari.core.TripConfig
import com.sparnai.bari.core.sources.GoogleFlightsParser
import com.sparnai.bari.core.sources.GoogleFlightsQuery
import com.sparnai.bari.core.sources.PriceFetcher
import com.sparnai.bari.core.title
import com.sparnai.bari.core.withError
import com.sparnai.bari.core.withFare
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Duration
import java.time.LocalDate

/**
 * Live check of all price sources (runs in CI and locally):
 *   check [config/trip.json]      – fetch every segment of the trip and rank the journeys
 *   discover                      – find Ryanair one-stop self-transfer connections to/from Bari
 *   google FROM TO DATE           – show what the Google Flights page returns
 */
fun main(args: Array<String>) = runBlocking {
    val fetcher = PriceFetcher()
    when (args.firstOrNull()) {
        "check" -> check(fetcher, File(args.getOrElse(1) { "config/trip.json" }))
        "discover" -> discover(fetcher, args.drop(1))
        "google" -> google(fetcher, args[1], args[2], args[3])
        else -> println("Usage: check [trip.json] | discover | google FROM TO DATE")
    }
}

private fun fareText(f: Fare?): String = if (f == null) "—" else
    "${Format.euro(f.price)}  ${f.flightNumber}  ${f.departure?.drop(11)?.take(5) ?: "?"}→${f.arrival?.drop(11)?.take(5) ?: "?"}" +
        (f.stops?.takeIf { it > 0 }?.let { "  ($it persėd.)" } ?: "")

private suspend fun check(fetcher: PriceFetcher, file: File) {
    val trip = BariJson.decodeFromString(TripConfig.serializer(), file.readText())
    val now = System.currentTimeMillis()
    val records = mutableMapOf<String, SegmentRecord>()
    var ok = 0
    println("== Segments (${trip.segments.size}) ==")
    for (segment in trip.segments) {
        if (segment.source == FareSource.GOOGLE) delay(1500)
        val result = runCatching { fetcher.fetch(segment) }
        val fare = result.getOrNull()
        val error = result.exceptionOrNull()
        records[segment.id] = when {
            fare != null -> SegmentRecord().withFare(fare, now, segment.signature).also { ok++ }
            else -> SegmentRecord().withError(error?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "nerasta", now, segment.signature)
        }
        val status = if (fare != null) "OK  " else "FAIL"
        println("$status %-11s %-14s %-40s %s".format(segment.source.title, segment.id, segment.label.take(40),
            if (fare != null) fareText(fare) else records[segment.id]?.lastError))
    }
    println("\n$ok/${trip.segments.size} segments priced\n")
    val plan = RouteMath.plan(trip, records)
    fun section(title: String, list: List<RouteSummary>) {
        println("== $title ==")
        list.forEach { r ->
            val stops = r.layovers.filterNotNull().joinToString(", ") { "${it.level} ${Format.city(it.at)} ${Format.duration(it.minutes)}" }
            println("  %-12s %-45s %s".format(Format.euro(r.total), r.route.name.take(45),
                if (r.missing.isNotEmpty()) "trūksta: ${r.missing.joinToString()}" else stops))
        }
    }
    section("Nuvykimas", plan.outbound)
    section("Grįžimas", plan.back)
    section("Į abi puses", plan.round)
    println("\nPigiausia kelionė: ${Format.euro(plan.bestTotal)}" +
        (if (plan.roundTripWins) " (${plan.bestRound?.route?.name})" else " (${plan.bestOut?.route?.name} + ${plan.bestBack?.route?.name})"))
}

/** Ryanair self-transfer discovery: ORIGIN → X → DEST with at least [minLayover] between flights. */
private suspend fun discover(fetcher: PriceFetcher, args: List<String>) {
    val minLayover = Duration.ofMinutes(120)
    suspend fun connections(origins: List<String>, dest: String, date: String, secondDates: List<String>) {
        println("== ${origins.joinToString("/")} → ? → $dest, $date ==")
        val found = mutableListOf<Triple<Double, String, String>>()
        for (origin in origins) {
            val first = runCatching { fetcher.ryanair.faresFrom(origin, date) }.getOrElse {
                println("  $origin: ${it.message}"); emptyList()
            }
            first.firstOrNull { it.first == dest }?.let { (_, f) ->
                found += Triple(f.price, "$origin→$dest tiesioginis", fareText(f))
            }
            for ((hub, f1) in first) {
                if (hub == dest) continue
                val arrival = RouteMath.parseLocal(f1.arrival) ?: continue
                for (d2 in secondDates) {
                    val sameDay = d2 == arrival.toLocalDate().toString()
                    val earliest = if (sameDay) arrival.plus(minLayover) else LocalDate.parse(d2).atStartOfDay()
                    if (earliest.toLocalDate().toString() != d2) continue
                    val seg = Segment(id = "x", label = "x", origin = hub, dest = dest, date = d2,
                        timeFrom = "%02d:%02d".format(earliest.hour, earliest.minute))
                    val f2 = runCatching { fetcher.ryanair.cheapestFare(seg) }.getOrNull() ?: continue
                    found += Triple(f1.price + f2.price, "$origin→$hub→$dest ($d2)", "${fareText(f1)}  |  ${fareText(f2)}")
                }
            }
        }
        found.sortedBy { it.first }.take(25).forEach { (total, name, detail) ->
            println("  %-10s %-28s %s".format(Format.euro(total), name, detail))
        }
    }
    val out = args.getOrElse(0) { "2026-12-26" }
    val back = args.getOrElse(1) { "2027-01-03" }
    val backEve = LocalDate.parse(back).minusDays(1).toString()
    connections(listOf("VNO", "KUN", "RIX"), "BRI", out, listOf(out))
    connections(listOf("VNO", "KUN", "RIX"), "BDS", out, listOf(out))
    connections(listOf("BRI", "BDS"), "VNO", back, listOf(back))
    connections(listOf("BRI", "BDS"), "VNO", backEve, listOf(backEve, back))
    connections(listOf("BRI"), "KUN", back, listOf(back))
}

private suspend fun google(fetcher: PriceFetcher, from: String, to: String, date: String) {
    val seg = Segment(id = "g", label = "g", origin = from, dest = to, date = date, source = FareSource.GOOGLE)
    val url = GoogleFlightsQuery.searchUrl(seg)
    println("URL: $url")
    val html = fetcher.http.get(url, GoogleFlightsQuery.HEADERS)
    println("HTML length: ${html.length}; problem: ${GoogleFlightsParser.problem(html)}")
    val labels = Regex("aria-label=\"([^\"]*)\"").findAll(html).map { it.groupValues[1] }
        .filter { it.contains("euro", ignoreCase = true) }.toList()
    println("aria-labels with 'euro': ${labels.size}")
    labels.take(6).forEach { println("  LABEL: ${it.take(400)}") }
    val flights = GoogleFlightsParser.parse(html, LocalDate.parse(date))
    println("Parsed flights: ${flights.size}")
    flights.sortedBy { it.price }.take(8).forEach { println("  $it") }
    if (labels.isEmpty()) {
        println("Snippet: " + html.take(1500).replace("\n", " "))
    }
}
