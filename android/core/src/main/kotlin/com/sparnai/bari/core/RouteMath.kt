package com.sparnai.bari.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.sparnai.bari.core.sources.GoogleFlightsQuery
import java.time.format.DateTimeFormatter

/**
 * OK – comfortable; TIGHT/RISKY/IMPOSSIBLE – short self-transfer; LONG – 5 h+ wait the same day;
 * OVERNIGHT – the wait spans the night; STAY – a day or more (the holiday itself).
 */
enum class LayoverLevel { OK, TIGHT, RISKY, IMPOSSIBLE, LONG, OVERNIGHT, STAY }

/** What the traveller has to do between two legs. */
enum class Transfer { NONE, AIRPORT_CHANGE, TO_AIRPORT, TO_STATION, STATION_CHANGE }

data class Layover(
    val minutes: Long,
    val level: LayoverLevel,
    /** Arrival and next departure are at different places (e.g. bus station → airport). */
    val airportChange: Boolean,
    val at: String,
    val transfer: Transfer = Transfer.NONE,
)

data class Leg(
    val segment: Segment,
    val fare: Fare?,
    val previousPrice: Double?,
    val departure: LocalDateTime?,
    val arrival: LocalDateTime?,
    /** False when the price scope leaves this leg out of the total (e.g. a bus in "flights only"). */
    val included: Boolean = true,
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
    val scope: PriceScope = PriceScope.FULL,
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

data class TripPlan(
    val outbound: List<RouteSummary>,
    val back: List<RouteSummary>,
    val round: List<RouteSummary>,
    val bestOut: RouteSummary?,
    val bestBack: RouteSummary?,
    val bestRound: RouteSummary?,
    /** Cheapest whole trip: best outbound + best return, or a round-trip ticket if cheaper. */
    val bestTotal: Double?,
    val previousBestTotal: Double?,
    val roundTripWins: Boolean,
)

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
        val transfer = when {
            sameAirport -> Transfer.NONE
            prev.segment.mode != TransportMode.FLIGHT && next.segment.mode == TransportMode.FLIGHT -> Transfer.TO_AIRPORT
            prev.segment.mode == TransportMode.FLIGHT && next.segment.mode != TransportMode.FLIGHT -> Transfer.TO_STATION
            next.segment.mode == TransportMode.FLIGHT -> Transfer.AIRPORT_CHANGE
            else -> Transfer.STATION_CHANGE
        }
        return Layover(minutes, level, !sameAirport, next.segment.origin, transfer)
    }

    fun summarize(
        route: RouteOption,
        trip: TripConfig,
        records: Map<String, SegmentRecord>,
        scope: PriceScope = PriceScope.FULL,
    ): RouteSummary {
        val byId = trip.segments.associateBy { it.id }
        val legs = mutableListOf<Leg>()
        val missing = mutableListOf<String>()
        for (id in route.segments) {
            val segment = byId[id]
            if (segment == null) {
                missing += id
                continue
            }
            val included = scope == PriceScope.FULL || segment.mode == TransportMode.FLIGHT
            val leg = leg(segment, records[id]).copy(included = included)
            if (included && leg.fare == null) missing += segment.label
            legs += leg
        }
        val counted = legs.filter { it.included }
        val extra = if (scope == PriceScope.FULL) route.extraCost else 0.0
        val pax = trip.passengers.coerceAtLeast(1)
        val complete = missing.isEmpty() && counted.isNotEmpty()
        val perPerson = if (complete) round2(counted.sumOf { it.price ?: 0.0 } + extra) else null
        val previous = if (complete) round2(counted.sumOf { it.previousPrice ?: it.price ?: 0.0 } + extra) else null
        return RouteSummary(
            route = route,
            legs = legs,
            layovers = legs.zipWithNext { a, b -> layover(a, b) },
            perPerson = perPerson,
            total = perPerson?.let { round2(it * pax) },
            previousTotal = previous?.let { round2(it * pax) },
            missing = missing,
            scope = scope,
        )
    }

    /** All routes, cheapest first; incomplete routes last. */
    fun summarizeAll(
        trip: TripConfig,
        records: Map<String, SegmentRecord>,
        scope: PriceScope = PriceScope.FULL,
    ): List<RouteSummary> =
        trip.routes.map { summarize(it, trip, records, scope) }
            .sortedWith(compareBy<RouteSummary> { it.total == null }.thenBy { it.total ?: 0.0 })

    /** Ranks outbound, return and round-trip journeys and finds the cheapest whole trip. */
    fun plan(
        trip: TripConfig,
        records: Map<String, SegmentRecord>,
        scope: PriceScope = PriceScope.FULL,
    ): TripPlan {
        val all = summarizeAll(trip, records, scope)
        val out = all.filter { it.route.direction == Direction.OUT }
        val back = all.filter { it.route.direction == Direction.BACK }
        val round = all.filter { it.route.direction == Direction.ROUND }
        val bestOut = out.firstOrNull { it.total != null }
        val bestBack = back.firstOrNull { it.total != null }
        val bestRound = round.firstOrNull { it.total != null }
        val combo = if (bestOut?.total != null && bestBack?.total != null) round2(bestOut.total + bestBack.total) else null
        val comboPrev = if (bestOut?.previousTotal != null && bestBack?.previousTotal != null) {
            round2(bestOut.previousTotal + bestBack.previousTotal)
        } else {
            null
        }
        val roundWins = bestRound?.total != null && (combo == null || bestRound.total < combo)
        return TripPlan(
            outbound = out,
            back = back,
            round = round,
            bestOut = bestOut,
            bestBack = bestBack,
            bestRound = bestRound,
            bestTotal = if (roundWins) bestRound?.total else combo,
            previousBestTotal = if (roundWins) bestRound?.previousTotal else comboPrev,
            roundTripWins = roundWins,
        )
    }

    /** Notification lines for one segment after a successful check. */
    /** Notification lines for one segment after a successful check; amounts are for all [passengers]. */
    fun alertsFor(
        segment: Segment,
        before: SegmentRecord?,
        fare: Fare,
        settings: AppSettings,
        passengers: Int = 1,
    ): List<String> {
        val pax = passengers.coerceAtLeast(1)
        val who = if (pax > 1) " (${Format.passengers(pax)})" else ""
        val out = mutableListOf<String>()
        val usable = before?.takeIf { it.signature == null || it.signature == segment.signature }
        val old = usable?.fare?.price
        if (settings.notifyOnDrop && old != null) {
            val drop = round2((old - fare.price) * pax)
            if (drop > 0.0 && drop >= settings.minDropEuro) {
                val lowest = usable?.lowest
                val record = if (lowest != null && fare.price < lowest) " · žemiausia kada nors!" else ""
                out += "📉 ${segment.label}: ${Format.total(fare.price, pax)}$who " +
                    "(buvo ${Format.total(old, pax)}, −${Format.euro(drop)})$record"
            }
        }
        val target = segment.targetPrice
        if (settings.notifyOnTarget && target != null && fare.price <= target && (old == null || old > target)) {
            out += "🎯 ${segment.label}: ${Format.total(fare.price, pax)}$who – pasiekta tikslinė kaina (${Format.total(target, pax)})"
        }
        return out
    }

    fun ryanairUrl(origin: String, dest: String, date: String, adults: Int = 1): String =
        "https://www.ryanair.com/gb/en/trip/flights/select?adults=${adults.coerceAtLeast(1)}" +
            "&teens=0&children=0&infants=0&dateOut=$date&isConnectedFlight=false" +
            "&isReturn=false&discount=0&originIata=$origin&destinationIata=$dest"

    /** Where the user can see / book the fare. */
    fun bookingUrl(segment: Segment, fare: Fare?, passengers: Int = 1): String = when (segment.source) {
        FareSource.RYANAIR -> ryanairUrl(segment.origin, segment.dest, segment.date, passengers)
        FareSource.GOOGLE -> GoogleFlightsQuery.searchUrl(segment, passengers)
        FareSource.TRENITALIA -> fare?.url ?: "https://www.lefrecce.it/Channels.Website.WEB/"
        FareSource.FLIXBUS -> fare?.url?.replace("adult=1", "adult=${passengers.coerceAtLeast(1)}") ?: "https://www.flixbus.com/"
        FareSource.MANUAL -> GoogleFlightsQuery.searchUrl(segment, passengers)
    }

    fun newSegmentId(origin: String, dest: String, date: String, existing: Collection<String>): String {
        fun code(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]+"), "").take(6).ifBlank { "X" }
        val base = "${code(origin)}_${code(dest)}_${date.replace("-", "")}"
        if (base !in existing) return base
        var i = 2
        while ("${base}_$i" in existing) i++
        return "${base}_$i"
    }
}
