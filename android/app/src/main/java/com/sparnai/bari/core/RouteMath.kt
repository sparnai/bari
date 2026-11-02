package com.sparnai.bari.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * OK – comfortable; TIGHT/RISKY/IMPOSSIBLE – short self-transfer; LONG – 5 h+ wait the same day;
 * OVERNIGHT – the wait spans the night; STAY – a day or more (the holiday itself).
 */
enum class LayoverLevel { OK, TIGHT, RISKY, IMPOSSIBLE, LONG, OVERNIGHT, STAY }

data class Layover(
    val minutes: Long,
    val level: LayoverLevel,
    /** Arrival and next departure are at different airports (e.g. train station → CIA). */
    val airportChange: Boolean,
    val at: String,
)

data class Leg(
    val segment: Segment,
    val fare: Fare?,
    val previousPrice: Double?,
    val departure: LocalDateTime?,
    val arrival: LocalDateTime?,
) {
    val price: Double? get() = fare?.price
}

data class RouteSummary(
    val route: RouteOption,
    val legs: List<Leg>,
    /** layovers[i] is between legs[i] and legs[i+1]; null when times are unknown. */
    val layovers: List<Layover?>,
    val perPerson: Double?,
    val total: Double?,
    val previousTotal: Double?,
    val missing: List<String>,
) {
    val worstLayover: LayoverLevel?
        get() = layovers.filterNotNull().map { it.level }.maxByOrNull { severity(it) }

    companion object {
        fun severity(level: LayoverLevel): Int = when (level) {
            LayoverLevel.OK, LayoverLevel.LONG, LayoverLevel.OVERNIGHT, LayoverLevel.STAY -> 0
            LayoverLevel.TIGHT -> 1
            LayoverLevel.RISKY -> 2
            LayoverLevel.IMPOSSIBLE -> 3
        }
    }
}

object RouteMath {
    private val isoMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun parseLocal(value: String?): LocalDateTime? {
        if (value.isNullOrBlank() || value.length < 16) return null
        return runCatching { LocalDateTime.parse(value.take(16), isoMinute) }.getOrNull()
    }

    fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(value.trim().padStart(5, '0')) }.getOrNull()
    }

    /** The fare a manual segment represents (null when no price was entered). */
    fun manualFare(segment: Segment): Fare? {
        val price = segment.price ?: return null
        val date = parseDate(segment.date) ?: return Fare(price = price, flightNumber = segment.flight.orEmpty())
        val dep = parseTime(segment.departure)?.let { date.atTime(it) }
        var arr = parseTime(segment.arrival)?.let { date.atTime(it) }
        if (dep != null && arr != null && arr.isBefore(dep)) arr = arr.plusDays(1)
        return Fare(
            price = round2(price),
            flightNumber = segment.flight.orEmpty(),
            departure = dep?.format(isoMinute),
            arrival = arr?.format(isoMinute),
        )
    }

    fun leg(segment: Segment, record: SegmentRecord?): Leg {
        val usable = record?.takeIf { it.signature == null || it.signature == segment.signature }
        val fare = if (segment.isManual) manualFare(segment) else usable?.fare
        return Leg(
            segment = segment,
            fare = fare,
            previousPrice = usable?.previousPrice,
            departure = parseLocal(fare?.departure),
            arrival = parseLocal(fare?.arrival),
        )
    }

    fun layover(prev: Leg, next: Leg): Layover? {
        val arr = prev.arrival ?: return null
        val dep = next.departure ?: return null
        val minutes = Duration.between(arr, dep).toMinutes()
        val sameAirport = prev.segment.dest.equals(next.segment.origin, ignoreCase = true)
        // The wait covers the night if 03:00 falls between arrival and departure.
        val night = arr.toLocalDate().atTime(3, 0).let { if (it.isBefore(arr)) it.plusDays(1) else it }
        val level = when {
            minutes < 0 -> LayoverLevel.IMPOSSIBLE
            minutes < 60 -> LayoverLevel.RISKY
            minutes < (if (sameAirport) 120 else 180) -> LayoverLevel.TIGHT
            minutes >= 24 * 60 -> LayoverLevel.STAY
            !night.isAfter(dep) -> LayoverLevel.OVERNIGHT
            minutes >= 5 * 60 -> LayoverLevel.LONG
            else -> LayoverLevel.OK
        }
        return Layover(minutes, level, !sameAirport, next.segment.origin)
    }

    fun summarize(route: RouteOption, trip: TripConfig, records: Map<String, SegmentRecord>): RouteSummary {
        val byId = trip.segments.associateBy { it.id }
        val legs = mutableListOf<Leg>()
        val missing = mutableListOf<String>()
        for (id in route.segments) {
            val segment = byId[id]
            if (segment == null) {
                missing += id
                continue
            }
            val leg = leg(segment, records[id])
            if (leg.fare == null) missing += segment.label
            legs += leg
        }
        val pax = trip.passengers.coerceAtLeast(1)
        val complete = missing.isEmpty() && legs.isNotEmpty()
        val perPerson = if (complete) round2(legs.sumOf { it.price ?: 0.0 }) else null
        val previous = if (complete) round2(legs.sumOf { it.previousPrice ?: it.price ?: 0.0 }) else null
        return RouteSummary(
            route = route,
            legs = legs,
            layovers = legs.zipWithNext { a, b -> layover(a, b) },
            perPerson = perPerson,
            total = perPerson?.let { round2(it * pax + route.extraCost) },
            previousTotal = previous?.let { round2(it * pax + route.extraCost) },
            missing = missing,
        )
    }

    /** All routes, cheapest first; incomplete routes last. */
    fun summarizeAll(trip: TripConfig, records: Map<String, SegmentRecord>): List<RouteSummary> =
        trip.routes.map { summarize(it, trip, records) }
            .sortedWith(compareBy<RouteSummary> { it.total == null }.thenBy { it.total ?: 0.0 })

    /** Notification lines for one segment after a successful check. */
    fun alertsFor(segment: Segment, before: SegmentRecord?, fare: Fare, settings: AppSettings): List<String> {
        val out = mutableListOf<String>()
        val usable = before?.takeIf { it.signature == null || it.signature == segment.signature }
        val old = usable?.fare?.price
        if (settings.notifyOnDrop && old != null) {
            val drop = round2(old - fare.price)
            if (drop > 0.0 && drop >= settings.minDropEuro) {
                val lowest = usable?.lowest
                val record = if (lowest != null && fare.price < lowest) " · žemiausia kada nors!" else ""
                out += "📉 ${segment.label}: ${Format.euro(fare.price)} (buvo ${Format.euro(old)}, −${Format.euro(drop)})$record"
            }
        }
        val target = segment.targetPrice
        if (settings.notifyOnTarget && target != null && fare.price <= target && (old == null || old > target)) {
            out += "🎯 ${segment.label}: ${Format.euro(fare.price)} – pasiekta tikslinė kaina (${Format.euro(target)})"
        }
        return out
    }

    /** Ryanair booking page for a segment, or a Google Flights search for other carriers. */
    fun bookingUrl(segment: Segment, passengers: Int = 1): String =
        if (!segment.isManual) {
            "https://www.ryanair.com/gb/en/trip/flights/select?adults=${passengers.coerceAtLeast(1)}" +
                "&teens=0&children=0&infants=0&dateOut=${segment.date}&isConnectedFlight=false" +
                "&isReturn=false&discount=0&originIata=${segment.origin}&destinationIata=${segment.dest}"
        } else {
            "https://www.google.com/travel/flights?q=Flights%20from%20${segment.origin}%20to%20${segment.dest}%20on%20${segment.date}"
        }

    fun newSegmentId(origin: String, dest: String, date: String, existing: Collection<String>): String {
        val base = "${origin}_${dest}_${date.replace("-", "")}".uppercase()
        if (base !in existing) return base
        var i = 2
        while ("${base}_$i" in existing) i++
        return "${base}_$i"
    }
}
